package com.example.reversi.service;

import com.example.reversi.model.*;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GameService {
    private static final int INF = 1_000_000;

    private final BoardService boardService;
    private final AiService aiService;

    private static final int[][] RECOMMEND_WEIGHTS = {
            {140, -45, 25, 8, 8, 25, -45, 140},
            {-45, -90, -12, -8, -8, -12, -90, -45},
            {25, -12, 18, 4, 4, 18, -12, 25},
            {8, -8, 4, 2, 2, 4, -8, 8},
            {8, -8, 4, 2, 2, 4, -8, 8},
            {25, -12, 18, 4, 4, 18, -12, 25},
            {-45, -90, -12, -8, -12, -12, -90, -45},
            {140, -45, 25, 8, 8, 25, -45, 140}
    };

    public GameService(BoardService boardService, AiService aiService) {
        this.boardService = boardService;
        this.aiService = aiService;
    }

    public Board newGame(Difficulty difficulty) {
        Board board = boardService.createInitialBoard();
        board.setDifficulty(difficulty == null ? Difficulty.NORMAL : difficulty);
        board.setMessage("あなたの番です。緑の候補マスをクリックしてください。");
        return board;
    }

    /** 通常フォーム用: ユーザー着手後、そのままCPUまで進める。 */
    public void playerMove(Board board, int row, int col) {
        if (board.isGameOver()) return;

        if (playerMustPass(board)) {
            passPlayer(board);
            return;
        }

        if (!boardService.isLegalMove(board.getCells(), row, col, Board.BLACK)) {
            board.setMessage("その場所には置けません。緑の候補マスを選んでください。");
            return;
        }

        boardService.applyMove(board.getCells(), new Move(row, col), Board.BLACK);
        board.setLastMoveRow(row);
        board.setLastMoveCol(col);
        boardService.updateCounts(board);

        if (finishIfGameOver(board)) return;
        playCpuTurnsUntilPlayerCanMove(board, "");
    }

    /**
     * Ajax用: 黒石だけ先に置いて返す。
     * 戻り値 true の場合、ブラウザ側が /api/cpu-move を呼び出す。
     */
    public boolean playerMoveOnly(Board board, int row, int col) {
        if (board == null || board.isGameOver()) return false;

        if (playerMustPass(board)) {
            passPlayer(board);
            return false;
        }

        if (!boardService.isLegalMove(board.getCells(), row, col, Board.BLACK)) {
            board.setMessage("その場所には置けません。緑の候補マスを選んでください。");
            return false;
        }

        boardService.applyMove(board.getCells(), new Move(row, col), Board.BLACK);
        board.setLastMoveRow(row);
        board.setLastMoveCol(col);
        boardService.updateCounts(board);

        if (finishIfGameOver(board)) return false;

        boolean cpuCanMove = !boardService.getLegalMoves(board.getCells(), Board.WHITE).isEmpty();
        if (!cpuCanMove) {
            if (boardService.getLegalMoves(board.getCells(), Board.BLACK).isEmpty()) {
                finishGame(board);
                return false;
            }
            board.setMessage("CPUは置ける場所がないためパスしました。あなたの番です。");
            return false;
        }

        board.setMessage("CPUが考えています...");
        return true;
    }

    /** Ajax用: CPUの手だけ進める。 */
    public void cpuMove(Board board) {
        if (board == null || board.isGameOver()) return;
        playCpuTurnsUntilPlayerCanMove(board, "");
    }

    public void passPlayer(Board board) {
        if (board == null || board.isGameOver()) return;

        if (!boardService.getLegalMoves(board.getCells(), Board.BLACK).isEmpty()) {
            board.setMessage("まだ置ける場所があります。緑の候補マスを選んでください。");
            return;
        }

        if (boardService.getLegalMoves(board.getCells(), Board.WHITE).isEmpty()) {
            finishGame(board);
            return;
        }

        playCpuTurnsUntilPlayerCanMove(board, "あなたは置ける場所がないためパスしました。");
    }

    public boolean playerMustPass(Board board) {
        return board != null
                && !board.isGameOver()
                && boardService.getLegalMoves(board.getCells(), Board.BLACK).isEmpty()
                && !boardService.getLegalMoves(board.getCells(), Board.WHITE).isEmpty();
    }

    public Set<String> legalMoveKeys(Board board) {
        Set<String> keys = new HashSet<>();
        if (board != null && !board.isGameOver()) {
            for (Move m : boardService.getLegalMoves(board.getCells(), Board.BLACK)) {
                keys.add(m.getRow() + "-" + m.getCol());
            }
        }
        return keys;
    }

    public String recommendedMoveKey(Board board) {
        if (board == null || board.isGameOver() || playerMustPass(board)) return "";
        List<Move> moves = boardService.getLegalMoves(board.getCells(), Board.BLACK);
        if (moves.isEmpty()) return "";

        int empty = boardService.countEmpty(board.getCells());
        int depth = empty <= 10 ? empty + 2 : (empty <= 18 ? 6 : 4);
        moves.sort((a, b) -> Integer.compare(movePriority(board.getCells(), b, Board.BLACK), movePriority(board.getCells(), a, Board.BLACK)));

        Move best = moves.get(0);
        int bestScore = -INF;
        int alpha = -INF;

        for (Move move : moves) {
            int[][] next = boardService.copyCells(board.getCells());
            boardService.applyMove(next, move, Board.BLACK);
            int score = recommendSearch(next, depth - 1, alpha, INF, Board.WHITE);
            score += movePriority(board.getCells(), move, Board.BLACK) / 4;
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
            alpha = Math.max(alpha, bestScore);
        }
        return best.getRow() + "-" + best.getCol();
    }

    private int recommendSearch(int[][] cells, int depth, int alpha, int beta, int player) {
        boolean terminal = boardService.isBoardFull(cells) ||
                (boardService.getLegalMoves(cells, Board.BLACK).isEmpty() && boardService.getLegalMoves(cells, Board.WHITE).isEmpty());
        if (terminal) return terminalScoreForBlack(cells);
        if (depth <= 0) return evaluateForBlack(cells);

        List<Move> moves = boardService.getLegalMoves(cells, player);
        if (moves.isEmpty()) return recommendSearch(cells, depth - 1, alpha, beta, boardService.opponent(player));
        moves.sort((a, b) -> Integer.compare(movePriority(cells, b, player), movePriority(cells, a, player)));

        if (player == Board.BLACK) {
            int value = -INF;
            for (Move move : moves) {
                int[][] next = boardService.copyCells(cells);
                boardService.applyMove(next, move, Board.BLACK);
                value = Math.max(value, recommendSearch(next, depth - 1, alpha, beta, Board.WHITE));
                alpha = Math.max(alpha, value);
                if (alpha >= beta) break;
            }
            return value;
        } else {
            int value = INF;
            for (Move move : moves) {
                int[][] next = boardService.copyCells(cells);
                boardService.applyMove(next, move, Board.WHITE);
                value = Math.min(value, recommendSearch(next, depth - 1, alpha, beta, Board.BLACK));
                beta = Math.min(beta, value);
                if (alpha >= beta) break;
            }
            return value;
        }
    }

    private int evaluateForBlack(int[][] cells) {
        int score = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (cells[r][c] == Board.BLACK) score += RECOMMEND_WEIGHTS[r][c];
                if (cells[r][c] == Board.WHITE) score -= RECOMMEND_WEIGHTS[r][c];
            }
        }
        int empty = boardService.countEmpty(cells);
        score += (boardService.getLegalMoves(cells, Board.BLACK).size() - boardService.getLegalMoves(cells, Board.WHITE).size()) * 45;
        score += cornerDiff(cells) * 360;
        score += dangerDiff(cells);
        score += stableEdgeDiff(cells) * 22;
        int discDiffForBlack = -boardService.discDiffForWhite(cells);
        int discWeight = empty > 42 ? 1 : (empty > 16 ? 4 : 14);
        score += discDiffForBlack * discWeight;
        return score;
    }

    private int terminalScoreForBlack(int[][] cells) {
        int diff = -boardService.discDiffForWhite(cells);
        if (diff > 0) return 10000 + diff;
        if (diff < 0) return -10000 + diff;
        return 0;
    }

    private int movePriority(int[][] cells, Move move, int player) {
        int r = move.getRow();
        int c = move.getCol();
        int score = 0;
        if (isCorner(r, c)) score += 10000;
        else if (isXSquare(r, c)) score -= 2600;
        else if (isCSquare(r, c)) score -= 1700;
        else if (isEdge(r, c)) score += 900;
        int[][] next = boardService.copyCells(cells);
        int flipped = boardService.applyMove(next, move, player);
        score += flipped * 30;
        score -= boardService.getLegalMoves(next, boardService.opponent(player)).size() * 60;
        return score;
    }

    private int cornerDiff(int[][] cells) {
        int score = 0;
        int[][] corners = {{0,0}, {0,7}, {7,0}, {7,7}};
        for (int[] p : corners) {
            if (cells[p[0]][p[1]] == Board.BLACK) score++;
            if (cells[p[0]][p[1]] == Board.WHITE) score--;
        }
        return score;
    }

    private int dangerDiff(int[][] cells) {
        int score = 0;
        int[][] xs = {{1,1}, {1,6}, {6,1}, {6,6}};
        int[][] cs = {{0,1}, {1,0}, {0,6}, {1,7}, {6,0}, {7,1}, {6,7}, {7,6}};
        for (int[] p : xs) {
            if (cells[p[0]][p[1]] == Board.BLACK) score -= 170;
            if (cells[p[0]][p[1]] == Board.WHITE) score += 170;
        }
        for (int[] p : cs) {
            if (cells[p[0]][p[1]] == Board.BLACK) score -= 110;
            if (cells[p[0]][p[1]] == Board.WHITE) score += 110;
        }
        return score;
    }

    private int stableEdgeDiff(int[][] cells) {
        return stableFromCorner(cells, 0, 0, 0, 1) + stableFromCorner(cells, 0, 0, 1, 0)
                + stableFromCorner(cells, 0, 7, 0, -1) + stableFromCorner(cells, 0, 7, 1, 0)
                + stableFromCorner(cells, 7, 0, 0, 1) + stableFromCorner(cells, 7, 0, -1, 0)
                + stableFromCorner(cells, 7, 7, 0, -1) + stableFromCorner(cells, 7, 7, -1, 0);
    }

    private int stableFromCorner(int[][] cells, int r, int c, int dr, int dc) {
        int owner = cells[r][c];
        if (owner == Board.EMPTY) return 0;
        int score = 0;
        while (r >= 0 && r < 8 && c >= 0 && c < 8 && cells[r][c] == owner) {
            score += owner == Board.BLACK ? 1 : -1;
            r += dr;
            c += dc;
        }
        return score;
    }

    private boolean isCorner(int r, int c) { return (r == 0 || r == 7) && (c == 0 || c == 7); }
    private boolean isEdge(int r, int c) { return r == 0 || r == 7 || c == 0 || c == 7; }
    private boolean isXSquare(int r, int c) { return (r == 1 && c == 1) || (r == 1 && c == 6) || (r == 6 && c == 1) || (r == 6 && c == 6); }
    private boolean isCSquare(int r, int c) {
        return (r == 0 && c == 1) || (r == 1 && c == 0) || (r == 0 && c == 6) || (r == 1 && c == 7) ||
               (r == 6 && c == 0) || (r == 7 && c == 1) || (r == 6 && c == 7) || (r == 7 && c == 6);
    }

    private void playCpuTurnsUntilPlayerCanMove(Board board, String prefixMessage) {
        StringBuilder message = new StringBuilder(prefixMessage == null ? "" : prefixMessage);
        int guard = 0;

        while (!board.isGameOver() && guard++ < 64) {
            boolean blackCanMove = !boardService.getLegalMoves(board.getCells(), Board.BLACK).isEmpty();
            boolean whiteCanMove = !boardService.getLegalMoves(board.getCells(), Board.WHITE).isEmpty();

            if (!blackCanMove && !whiteCanMove) {
                finishGame(board);
                return;
            }

            if (!whiteCanMove) {
                if (message.length() > 0) message.append(" ");
                message.append("CPUは置ける場所がないためパスしました。あなたの番です。");
                board.setMessage(message.toString());
                return;
            }

            SearchResult result = aiService.chooseMove(board.getCells(), board.getDifficulty());
            board.setCpuThinkingTimeMillis(result.getThinkingTimeMillis());
            board.setCpuEvaluation(result.getEvaluation());
            board.setSearchDepth(result.getDepth());

            if (result.getBestMove() != null) {
                boardService.applyMove(board.getCells(), result.getBestMove(), Board.WHITE);
                board.setLastMoveRow(result.getBestMove().getRow());
                board.setLastMoveCol(result.getBestMove().getCol());
            }
            boardService.updateCounts(board);

            if (finishIfGameOver(board)) return;

            blackCanMove = !boardService.getLegalMoves(board.getCells(), Board.BLACK).isEmpty();
            if (blackCanMove) {
                if (message.length() > 0) message.append(" ");
                message.append("CPUが置きました。あなたの番です。");
                board.setMessage(message.toString());
                return;
            }

            if (message.length() > 0) message.append(" ");
            message.append("あなたは置ける場所がないためパスしました。CPUが続けて打ちます。");
        }
        board.setMessage("パス処理を行いました。あなたの番です。");
    }

    private boolean finishIfGameOver(Board board) {
        if (boardService.isBoardFull(board.getCells()) ||
                (boardService.getLegalMoves(board.getCells(), Board.BLACK).isEmpty() && boardService.getLegalMoves(board.getCells(), Board.WHITE).isEmpty())) {
            finishGame(board);
            return true;
        }
        return false;
    }

    private void finishGame(Board board) {
        boardService.updateCounts(board);
        board.setGameOver(true);
        if (board.getBlackCount() > board.getWhiteCount()) board.setWinner("あなたの勝ち！");
        else if (board.getBlackCount() < board.getWhiteCount()) board.setWinner("CPUの勝ち！");
        else board.setWinner("引き分け！");
        board.setMessage("ゲーム終了: " + board.getWinner());
    }
}
