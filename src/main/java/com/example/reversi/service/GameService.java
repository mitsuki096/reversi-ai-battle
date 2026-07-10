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
        return newGame(difficulty, false);
    }

    public Board newGame(Difficulty difficulty, boolean localTwoPlayers) {
        Board board = boardService.createInitialBoard();
        board.setDifficulty(difficulty == null ? Difficulty.NORMAL : difficulty);
        board.setLocalTwoPlayers(localTwoPlayers);
        board.setCurrentTurn(Board.BLACK);
        boardService.updateCounts(board);

        if (localTwoPlayers) {
            board.setUserColor(Board.BLACK);
            board.setCpuColor(Board.WHITE);
            board.setMessage("ローカル2人対戦です。黒の番です。交互にクリックしてください。");
            return board;
        }

        boolean userFirst = random.nextBoolean();
        board.setUserColor(userFirst ? Board.BLACK : Board.WHITE);
        board.setCpuColor(userFirst ? Board.WHITE : Board.BLACK);

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
        if (board.isLocalTwoPlayers()) {
            localMove(board, row, col);
            return;
        }
        if (board.getCurrentTurn() != board.getUserColor()) {
            board.setMessage("現在はCPUの番です。");
            return;
        }
        if (!boardService.isLegalMove(board.getCells(), row, col, board.getUserColor())) {
            board.setMessage("その場所には置けません。緑の候補マスを選んでください。");
            return;
        }
        applyMove(board, row, col, board.getUserColor());
        if (finishIfGameOver(board))
            return;
        board.setCurrentTurn(board.getCpuColor());
        cpuMove(board);
    }

    public boolean playerMoveOnly(Board board, int row, int col) {
        if (board == null || board.isGameOver())
            return false;
        if (board.isLocalTwoPlayers()) {
            localMove(board, row, col);
            return false;
        }
        if (board.getCurrentTurn() != board.getUserColor())
            return false;
        if (!boardService.isLegalMove(board.getCells(), row, col, board.getUserColor())) {
            board.setMessage("その場所には置けません。緑の候補マスを選んでください。");
            return false;
        }

        applyMove(board, row, col, board.getUserColor());
        if (finishIfGameOver(board))
            return false;

        if (boardService.getLegalMoves(board.getCells(), board.getCpuColor()).isEmpty()) {
            board.setCurrentTurn(board.getCpuColor());
            autoPassIfNeeded(board);
            return false;
        }

        board.setCurrentTurn(board.getCpuColor());
        board.setMessage("CPUが考えています...");
        return true;
    }

    public boolean canPlayerMove(Board board, int row, int col) {
        if (board == null || board.isGameOver())
            return false;
        int player = board.isLocalTwoPlayers() ? board.getCurrentTurn() : board.getUserColor();
        if (!board.isLocalTwoPlayers() && board.getCurrentTurn() != player)
            return false;
        return boardService.isLegalMove(board.getCells(), row, col, player);
    }

    private void localMove(Board board, int row, int col) {
        int player = board.getCurrentTurn();
        if (!boardService.isLegalMove(board.getCells(), row, col, player)) {
            board.setMessage("その場所には置けません。" + colorLabelJa(player) + "の置ける場所を選んでください。");
            return;
        }

        applyMove(board, row, col, player);
        if (finishIfGameOver(board))
            return;
        board.setCurrentTurn(boardService.opponent(player));
        autoPassIfNeeded(board);
        if (!board.isGameOver()) {
            board.setMessage(colorLabelJa(board.getCurrentTurn()) + "の番です。");
        }
    }

    private void applyMove(Board board, int row, int col, int player) {
        boardService.applyMove(board.getCells(), new Move(row, col), player);
        board.setLastMoveRow(row);
        board.setLastMoveCol(col);
        boardService.updateCounts(board);
    }

    public void cpuMove(Board board) {
        if (board == null || board.isGameOver() || board.isLocalTwoPlayers())
            return;
        playCpuTurnsUntilUserCanMove(board, "");
    }

    public boolean playerMustPass(Board board) {
        if (board == null || board.isGameOver())
            return false;
        int player = board.getCurrentTurn();
        return boardService.getLegalMoves(board.getCells(), player).isEmpty()
                && !boardService.getLegalMoves(board.getCells(), boardService.opponent(player)).isEmpty();
    }

    public boolean autoPassIfNeeded(Board board) {
        if (board == null || board.isGameOver())
            return false;
        boolean passed = false;
        int guard = 0;

        while (!board.isGameOver() && guard++ < 2) {
            int player = board.getCurrentTurn();
            int opponent = boardService.opponent(player);
            boolean playerCanMove = !boardService.getLegalMoves(board.getCells(), player).isEmpty();
            boolean opponentCanMove = !boardService.getLegalMoves(board.getCells(), opponent).isEmpty();

            if (!playerCanMove && !opponentCanMove) {
                finishGame(board);
                return true;
            }
            if (playerCanMove)
                return passed;

            board.setCurrentTurn(opponent);
            passed = true;
            if (board.isLocalTwoPlayers()) {
                board.setMessage(colorLabelJa(player) + "は置ける場所がないため自動でパスしました。" + colorLabelJa(opponent) + "の番です。");
            } else if (player == board.getUserColor()) {
                board.setMessage("あなたは置ける場所がないため自動でパスしました。CPUが続けて打ちます。");
            } else {
                board.setMessage("CPUは置ける場所がないため自動でパスしました。あなたの番です。");
            }
        }
        return passed;
    }

    public Set<String> legalMoveKeys(Board board) {
        Set<String> keys = new HashSet<>();
        if (board != null && !board.isGameOver()) {
            int player = board.getCurrentTurn();
            if (board.isLocalTwoPlayers() || player == board.getUserColor()) {
                for (Move move : boardService.getLegalMoves(board.getCells(), player)) {
                    keys.add(move.getRow() + "-" + move.getCol());
                }
            }
        }
        return keys;
    }

    public String recommendedMoveKey(Board board) {
        if (board == null || board.isGameOver() || board.isLocalTwoPlayers()
                || board.getCurrentTurn() != board.getUserColor() || playerMustPass(board))
            return "";
        List<Move> moves = boardService.getLegalMoves(board.getCells(), board.getUserColor());
        if (moves.isEmpty())
            return "";
        Move best = moves.get(0);
        int bestScore = -INF;
        int empty = boardService.countEmpty(board.getCells());
        int depth = empty <= 10 ? empty + 2 : (empty <= 18 ? 6 : 4);
        int opponent = board.getCpuColor();
        for (Move move : moves) {
            int[][] next = boardService.copyCells(board.getCells());
            boardService.applyMove(next, move, board.getUserColor());
            int score = recommendSearch(next, depth - 1, -INF, INF, opponent, board.getUserColor());
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best.getRow() + "-" + best.getCol();
    }

    private int recommendSearch(int[][] cells, int depth, int alpha, int beta, int player, int userPlayer) {
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

        if (player == userPlayer) {
            int value = -INF;
            for (Move move : moves) {
                int[][] next = boardService.copyCells(cells);
                boardService.applyMove(next, move, player);
                value = Math.max(value,
                        recommendSearch(next, depth - 1, alpha, beta, boardService.opponent(player), userPlayer));
                alpha = Math.max(alpha, value);
                if (alpha >= beta)
                    break;
            }
            return value;
        } else {
            int value = INF;
            for (Move move : moves) {
                int[][] next = boardService.copyCells(cells);
                boardService.applyMove(next, move, player);
                value = Math.min(value,
                        recommendSearch(next, depth - 1, alpha, beta, boardService.opponent(player), userPlayer));
                beta = Math.min(beta, value);
                if (alpha >= beta)
                    break;
            }
            return value;
        }
    }

    private int evaluateForPlayer(int[][] cells, int player) {
        int opponent = boardService.opponent(player);
        int score = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (cells[r][c] == player)
                    score += RECOMMEND_WEIGHTS[r][c];
                if (cells[r][c] == opponent)
                    score -= RECOMMEND_WEIGHTS[r][c];
            }
        }
        score += (boardService.getLegalMoves(cells, player).size() - boardService.getLegalMoves(cells, opponent).size())
                * 45;
        score += discDiffForPlayer(cells, player) * 4;
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
                board.setMessage("CPUは置ける場所がないため自動でパスしました。あなたの番です。");
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
                message.append("CPUが置きました。あなたの番です。");
                board.setMessage(message.toString());
                return;
            }
            message.append("あなたは置ける場所がないため自動でパスしました。CPUが続けて打ちます。 ");
            board.setCurrentTurn(board.getCpuColor());
        }
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
        if (board.isLocalTwoPlayers()) {
            if (board.getBlackCount() > board.getWhiteCount())
                board.setWinner("黒の勝ち！");
            else if (board.getBlackCount() < board.getWhiteCount())
                board.setWinner("白の勝ち！");
            else
                board.setWinner("引き分け！");
        } else {
            int userCount = countColor(board.getCells(), board.getUserColor());
            int cpuCount = countColor(board.getCells(), board.getCpuColor());
            if (userCount > cpuCount)
                board.setWinner("あなたの勝ち！");
            else if (userCount < cpuCount)
                board.setWinner("CPUの勝ち！");
            else
                board.setWinner("引き分け！");
        }
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

    private int terminalScoreForPlayer(int[][] cells, int player) {
        int diff = discDiffForPlayer(cells, player);
        if (diff > 0)
            return 10000 + diff;
        if (diff < 0)
            return -10000 + diff;
        return 0;
    }

    private String colorLabelJa(int color) {
        return color == Board.BLACK ? "黒" : "白";
    }
}
