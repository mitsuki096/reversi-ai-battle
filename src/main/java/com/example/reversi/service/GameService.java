package com.example.reversi.service;

import com.example.reversi.model.*;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Service
public class GameService {
    private static final int INF = 1_000_000;

    private final BoardService boardService;
    private final AiService aiService;
    private final Random random = new Random();

    private static final int[][] RECOMMEND_WEIGHTS = {
            { 140, -45, 25, 8, 8, 25, -45, 140 },
            { -45, -90, -12, -8, -8, -12, -90, -45 },
            { 25, -12, 18, 4, 4, 18, -12, 25 },
            { 8, -8, 4, 2, 2, 4, -8, 8 },
            { 8, -8, 4, 2, 2, 4, -8, 8 },
            { 25, -12, 18, 4, 4, 18, -12, 25 },
            { -45, -90, -12, -8, -8, -12, -90, -45 },
            { 140, -45, 25, 8, 8, 25, -45, 140 }
    };

    public GameService(BoardService boardService, AiService aiService) {
        this.boardService = boardService;
        this.aiService = aiService;
    }

    public Board newGame(Difficulty difficulty) {
        Board board = boardService.createInitialBoard();
        board.setDifficulty(difficulty == null ? Difficulty.NORMAL : difficulty);

        boolean userFirst = random.nextBoolean();

        board.setUserColor(userFirst ? Board.BLACK : Board.WHITE);
        board.setCpuColor(userFirst ? Board.WHITE : Board.BLACK);
        board.setCurrentTurn(Board.BLACK);

        boardService.updateCounts(board);

        if (userFirst) {
            board.setMessage("あなたは先攻（黒）です。緑の候補マスをクリックしてください。");
        } else {
            board.setMessage("あなたは後攻（白）です。CPUが先攻として考えています...");
            cpuMove(board);

            if (!board.isGameOver()) {
                board.setMessage("あなたは後攻（白）です。CPUが先攻で置きました。あなたの番です。");
            }
        }

        return board;
    }

    public void playerMove(Board board, int row, int col) {
        if (board == null || board.isGameOver())
            return;
        if (board.getCurrentTurn() != board.getUserColor()) {
            board.setMessage("現在はCPUの番です。");
            return;
        }
        if (!boardService.isLegalMove(board.getCells(), row, col, board.getUserColor())) {
            board.setMessage("その場所には置けません。緑の候補マスを選んでください。");
            return;
        }
        applyUserMove(board, row, col);
        if (finishIfGameOver(board))
            return;
        cpuMove(board);
    }

    public boolean playerMoveOnly(Board board, int row, int col) {
        if (board == null || board.isGameOver())
            return false;
        if (board.getCurrentTurn() != board.getUserColor())
            return false;
        if (!boardService.isLegalMove(board.getCells(), row, col, board.getUserColor())) {
            board.setMessage("その場所には置けません。緑の候補マスを選んでください。");
            return false;
        }

        applyUserMove(board, row, col);
        if (finishIfGameOver(board))
            return false;

        if (boardService.getLegalMoves(board.getCells(), board.getCpuColor()).isEmpty()) {
            if (boardService.getLegalMoves(board.getCells(), board.getUserColor()).isEmpty()) {
                finishGame(board);
                return false;
            }
            board.setCurrentTurn(board.getUserColor());
            board.setMessage("CPUは置ける場所がないため自動でパスしました。あなたの番です。");
            return false;
        }

        board.setCurrentTurn(board.getCpuColor());
        board.setMessage("CPUが考えています...");
        return true;
    }

    private void applyUserMove(Board board, int row, int col) {
        boardService.applyMove(board.getCells(), new Move(row, col), board.getUserColor());
        board.setLastMoveRow(row);
        board.setLastMoveCol(col);
        boardService.updateCounts(board);
        board.setCurrentTurn(board.getCpuColor());
    }

    public void cpuMove(Board board) {
        if (board == null || board.isGameOver())
            return;
        playCpuTurnsUntilUserCanMove(board, "");
    }

    public void passPlayer(Board board) {
        autoPassIfNeeded(board);
    }

    public boolean playerMustPass(Board board) {
        return board != null
                && !board.isGameOver()
                && board.getCurrentTurn() == board.getUserColor()
                && boardService.getLegalMoves(board.getCells(), board.getUserColor()).isEmpty()
                && !boardService.getLegalMoves(board.getCells(), board.getCpuColor()).isEmpty();
    }

    public boolean autoPassIfNeeded(Board board) {
        if (!playerMustPass(board))
            return false;
        board.setMessage("あなたは置ける場所がないため自動でパスしました。");
        board.setCurrentTurn(board.getCpuColor());
        playCpuTurnsUntilUserCanMove(board, board.getMessage());
        return true;
    }

    public Set<String> legalMoveKeys(Board board) {
        Set<String> keys = new HashSet<>();
        if (board != null && !board.isGameOver() && board.getCurrentTurn() == board.getUserColor()) {
            for (Move move : boardService.getLegalMoves(board.getCells(), board.getUserColor())) {
                keys.add(move.getRow() + "-" + move.getCol());
            }
        }
        return keys;
    }

    public String recommendedMoveKey(Board board) {
        if (board == null || board.isGameOver() || board.getCurrentTurn() != board.getUserColor()
                || playerMustPass(board))
            return "";
        List<Move> moves = boardService.getLegalMoves(board.getCells(), board.getUserColor());
        if (moves.isEmpty())
            return "";

        int empty = boardService.countEmpty(board.getCells());
        int depth = empty <= 10 ? empty + 2 : (empty <= 18 ? 6 : 4);
        moves.sort((a, b) -> Integer.compare(movePriority(board.getCells(), b, board.getUserColor()),
                movePriority(board.getCells(), a, board.getUserColor())));

        Move best = moves.get(0);
        int bestScore = -INF;
        int alpha = -INF;
        int opponent = board.getCpuColor();

        for (Move move : moves) {
            int[][] next = boardService.copyCells(board.getCells());
            boardService.applyMove(next, move, board.getUserColor());
            int score = recommendSearch(next, depth - 1, alpha, INF, opponent, board.getUserColor());
            score += movePriority(board.getCells(), move, board.getUserColor()) / 4;
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
            alpha = Math.max(alpha, bestScore);
        }
        return best.getRow() + "-" + best.getCol();
    }

    private int recommendSearch(int[][] cells, int depth, int alpha, int beta, int player, int userPlayer) {
        int opponent = boardService.opponent(userPlayer);
        boolean terminal = boardService.isBoardFull(cells) ||
                (boardService.getLegalMoves(cells, Board.BLACK).isEmpty()
                        && boardService.getLegalMoves(cells, Board.WHITE).isEmpty());
        if (terminal)
            return terminalScoreForPlayer(cells, userPlayer);
        if (depth <= 0)
            return evaluateForPlayer(cells, userPlayer);

        List<Move> moves = boardService.getLegalMoves(cells, player);
        if (moves.isEmpty())
            return recommendSearch(cells, depth - 1, alpha, beta, boardService.opponent(player), userPlayer);
        moves.sort((a, b) -> Integer.compare(movePriority(cells, b, player), movePriority(cells, a, player)));

        if (player == userPlayer) {
            int value = -INF;
            for (Move move : moves) {
                int[][] next = boardService.copyCells(cells);
                boardService.applyMove(next, move, userPlayer);
                value = Math.max(value, recommendSearch(next, depth - 1, alpha, beta, opponent, userPlayer));
                alpha = Math.max(alpha, value);
                if (alpha >= beta)
                    break;
            }
            return value;
        } else {
            int value = INF;
            for (Move move : moves) {
                int[][] next = boardService.copyCells(cells);
                boardService.applyMove(next, move, opponent);
                value = Math.min(value, recommendSearch(next, depth - 1, alpha, beta, userPlayer, userPlayer));
                beta = Math.min(beta, value);
                if (alpha >= beta)
                    break;
            }
            return value;
        }
    }

    private int evaluateForPlayer(int[][] cells, int player) {
        int score = 0;
        int opponent = boardService.opponent(player);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (cells[r][c] == player)
                    score += RECOMMEND_WEIGHTS[r][c];
                if (cells[r][c] == opponent)
                    score -= RECOMMEND_WEIGHTS[r][c];
            }
        }
        int empty = boardService.countEmpty(cells);
        score += (boardService.getLegalMoves(cells, player).size() - boardService.getLegalMoves(cells, opponent).size())
                * 45;
        score += cornerDiff(cells, player) * 360;
        score += dangerDiff(cells, player);
        score += stableEdgeDiff(cells, player) * 22;
        score += discDiffForPlayer(cells, player) * (empty > 42 ? 1 : (empty > 16 ? 4 : 14));
        return score;
    }

    private int terminalScoreForPlayer(int[][] cells, int player) {
        int diff = discDiffForPlayer(cells, player);
        if (diff > 0)
            return 10000 + diff;
        if (diff < 0)
            return -10000 + diff;
        return 0;
    }

    private int movePriority(int[][] cells, Move move, int player) {
        int r = move.getRow();
        int c = move.getCol();
        int score = 0;
        if (isCorner(r, c))
            score += 10000;
        else if (isXSquare(r, c))
            score -= 2600;
        else if (isCSquare(r, c))
            score -= 1700;
        else if (isEdge(r, c))
            score += 900;
        int[][] next = boardService.copyCells(cells);
        int flipped = boardService.applyMove(next, move, player);
        score += flipped * 30;
        score -= boardService.getLegalMoves(next, boardService.opponent(player)).size() * 60;
        return score;
    }

    private void playCpuTurnsUntilUserCanMove(Board board, String prefixMessage) {
        StringBuilder message = new StringBuilder(prefixMessage == null ? "" : prefixMessage);
        int guard = 0;

        while (!board.isGameOver() && guard++ < 64) {
            boolean userCanMove = !boardService.getLegalMoves(board.getCells(), board.getUserColor()).isEmpty();
            boolean cpuCanMove = !boardService.getLegalMoves(board.getCells(), board.getCpuColor()).isEmpty();

            if (!userCanMove && !cpuCanMove) {
                finishGame(board);
                return;
            }

            if (!cpuCanMove) {
                board.setCurrentTurn(board.getUserColor());
                if (message.length() > 0)
                    message.append(" ");
                message.append("CPUは置ける場所がないため自動でパスしました。あなたの番です。");
                board.setMessage(message.toString());
                return;
            }

            board.setCurrentTurn(board.getCpuColor());
            SearchResult result = aiService.chooseMove(board.getCells(), board.getDifficulty(), board.getCpuColor());
            board.setCpuThinkingTimeMillis(result.getThinkingTimeMillis());
            board.setCpuEvaluation(result.getEvaluation());
            board.setSearchDepth(result.getDepth());

            if (result.getBestMove() != null) {
                boardService.applyMove(board.getCells(), result.getBestMove(), board.getCpuColor());
                board.setLastMoveRow(result.getBestMove().getRow());
                board.setLastMoveCol(result.getBestMove().getCol());
            }
            boardService.updateCounts(board);

            if (finishIfGameOver(board))
                return;

            userCanMove = !boardService.getLegalMoves(board.getCells(), board.getUserColor()).isEmpty();
            if (userCanMove) {
                board.setCurrentTurn(board.getUserColor());
                if (message.length() > 0)
                    message.append(" ");
                message.append("CPUが置きました。あなたの番です。");
                board.setMessage(message.toString());
                return;
            }

            if (message.length() > 0)
                message.append(" ");
            message.append("あなたは置ける場所がないため自動でパスしました。CPUが続けて打ちます。");
            board.setCurrentTurn(board.getCpuColor());
        }
        board.setMessage("パス処理を行いました。あなたの番です。");
    }

    private boolean finishIfGameOver(Board board) {
        if (boardService.isBoardFull(board.getCells()) ||
                (boardService.getLegalMoves(board.getCells(), Board.BLACK).isEmpty()
                        && boardService.getLegalMoves(board.getCells(), Board.WHITE).isEmpty())) {
            finishGame(board);
            return true;
        }
        return false;
    }

    private void finishGame(Board board) {
        boardService.updateCounts(board);
        board.setGameOver(true);
        board.setCurrentTurn(Board.EMPTY);
        int userCount = countColor(board.getCells(), board.getUserColor());
        int cpuCount = countColor(board.getCells(), board.getCpuColor());
        if (userCount > cpuCount)
            board.setWinner("あなたの勝ち！");
        else if (userCount < cpuCount)
            board.setWinner("CPUの勝ち！");
        else
            board.setWinner("引き分け！");
        board.setMessage("ゲーム終了: " + board.getWinner());
    }

    private int countColor(int[][] cells, int color) {
        int count = 0;
        for (int[] row : cells)
            for (int value : row)
                if (value == color)
                    count++;
        return count;
    }

    private int discDiffForPlayer(int[][] cells, int player) {
        return countColor(cells, player) - countColor(cells, boardService.opponent(player));
    }

    private int cornerDiff(int[][] cells, int player) {
        int score = 0;
        int opponent = boardService.opponent(player);
        int[][] corners = { { 0, 0 }, { 0, 7 }, { 7, 0 }, { 7, 7 } };
        for (int[] p : corners) {
            if (cells[p[0]][p[1]] == player)
                score++;
            if (cells[p[0]][p[1]] == opponent)
                score--;
        }
        return score;
    }

    private int dangerDiff(int[][] cells, int player) {
        int score = 0;
        int opponent = boardService.opponent(player);
        int[][] xs = { { 1, 1 }, { 1, 6 }, { 6, 1 }, { 6, 6 } };
        int[][] cs = { { 0, 1 }, { 1, 0 }, { 0, 6 }, { 1, 7 }, { 6, 0 }, { 7, 1 }, { 6, 7 }, { 7, 6 } };
        for (int[] p : xs) {
            if (cells[p[0]][p[1]] == player)
                score -= 170;
            if (cells[p[0]][p[1]] == opponent)
                score += 170;
        }
        for (int[] p : cs) {
            if (cells[p[0]][p[1]] == player)
                score -= 110;
            if (cells[p[0]][p[1]] == opponent)
                score += 110;
        }
        return score;
    }

    private int stableEdgeDiff(int[][] cells, int player) {
        return stableFromCorner(cells, 0, 0, 0, 1, player) + stableFromCorner(cells, 0, 0, 1, 0, player)
                + stableFromCorner(cells, 0, 7, 0, -1, player) + stableFromCorner(cells, 0, 7, 1, 0, player)
                + stableFromCorner(cells, 7, 0, 0, 1, player) + stableFromCorner(cells, 7, 0, -1, 0, player)
                + stableFromCorner(cells, 7, 7, 0, -1, player) + stableFromCorner(cells, 7, 7, -1, 0, player);
    }

    private int stableFromCorner(int[][] cells, int r, int c, int dr, int dc, int player) {
        int owner = cells[r][c];
        if (owner == Board.EMPTY)
            return 0;
        int score = 0;
        while (r >= 0 && r < 8 && c >= 0 && c < 8 && cells[r][c] == owner) {
            score += owner == player ? 1 : -1;
            r += dr;
            c += dc;
        }
        return score;
    }

    private boolean isCorner(int r, int c) {
        return (r == 0 || r == 7) && (c == 0 || c == 7);
    }

    private boolean isEdge(int r, int c) {
        return r == 0 || r == 7 || c == 0 || c == 7;
    }

    private boolean isXSquare(int r, int c) {
        return (r == 1 && c == 1) || (r == 1 && c == 6) || (r == 6 && c == 1) || (r == 6 && c == 6);
    }

    private boolean isCSquare(int r, int c) {
        return (r == 0 && c == 1) || (r == 1 && c == 0) || (r == 0 && c == 6) || (r == 1 && c == 7) ||
                (r == 6 && c == 0) || (r == 7 && c == 1) || (r == 6 && c == 7) || (r == 7 && c == 6);
    }
}
