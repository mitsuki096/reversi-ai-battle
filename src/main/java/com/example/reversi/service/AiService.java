package com.example.reversi.service;

import com.example.reversi.model.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiService {
    private static final int INF = 1_000_000;

    private final BoardService boardService;
    private final MoveOrderingService moveOrderingService;
    private final TranspositionTable transpositionTable;
    private long deadlineNanos;
    private int rootCpuPlayer;

    private static final int[][] WEIGHTS = {
            { 120, -30, 20, 5, 5, 20, -30, 120 },
            { -30, -60, -5, -5, -5, -5, -60, -30 },
            { 20, -5, 15, 3, 3, 15, -5, 20 },
            { 5, -5, 3, 3, 3, 3, -5, 5 },
            { 5, -5, 3, 3, 3, 3, -5, 5 },
            { 20, -5, 15, 3, 3, 15, -5, 20 },
            { -30, -60, -5, -5, -5, -5, -60, -30 },
            { 120, -30, 20, 5, 5, 20, -30, 120 }
    };

    public AiService(BoardService boardService,
            EvaluationService evaluationService,
            MoveOrderingService moveOrderingService,
            TranspositionTable transpositionTable) {
        this.boardService = boardService;
        this.moveOrderingService = moveOrderingService;
        this.transpositionTable = transpositionTable;
    }

    public SearchResult chooseMove(int[][] cells, Difficulty difficulty) {
        return chooseMove(cells, difficulty, Board.WHITE);
    }

    public SearchResult chooseMove(int[][] cells, Difficulty difficulty, int cpuPlayer) {
        long start = System.nanoTime();
        rootCpuPlayer = cpuPlayer;
        List<Move> legal = boardService.getLegalMoves(cells, cpuPlayer);
        if (legal.isEmpty()) {
            return new SearchResult(null, evaluate(cells, difficulty == Difficulty.ULTIMATE), 0, 0);
        }

        if (difficulty == Difficulty.ULTIMATE) {
            return chooseUltimate(cells, start, cpuPlayer);
        }

        int depth = difficulty.getDepth();
        moveOrderingService.orderMoves(cells, legal, cpuPlayer);
        Move best = legal.get(0);
        int bestScore = -INF;
        int opponent = boardService.opponent(cpuPlayer);

        for (Move move : legal) {
            int[][] next = boardService.copyCells(cells);
            boardService.applyMove(next, move, cpuPlayer);
            int score = alphabeta(next, depth - 1, -INF, INF, opponent, false, false);
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }

        long ms = (System.nanoTime() - start) / 1_000_000;
        return new SearchResult(best, bestScore, depth, ms);
    }

    private SearchResult chooseUltimate(int[][] cells, long start, int cpuPlayer) {
        transpositionTable.clear();
        deadlineNanos = start + 3_000_000_000L;
        int empty = boardService.countEmpty(cells);
        // 通常局面は難易度定義どおり深さ6まで反復深化する。残り12マス以下では
        // パスも考慮した終局探索を優先し、残りマス数に応じて探索を延長する。
        int maxDepth = empty <= 12 ? empty + 2 : Difficulty.ULTIMATE.getDepth();
        Move best = null;
        int bestScore = -INF;
        int completedDepth = 0;

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (System.nanoTime() >= deadlineNanos)
                break;
            SearchResult result = rootSearch(cells, depth, cpuPlayer);
            if (System.nanoTime() < deadlineNanos && result.getBestMove() != null) {
                best = result.getBestMove();
                bestScore = result.getEvaluation();
                completedDepth = depth;
            }
        }

        if (best == null) {
            List<Move> legal = boardService.getLegalMoves(cells, cpuPlayer);
            moveOrderingService.orderMoves(cells, legal, cpuPlayer);
            best = legal.get(0);
            bestScore = evaluate(cells, true);
        }

        long ms = (System.nanoTime() - start) / 1_000_000;
        return new SearchResult(best, bestScore, completedDepth, ms);
    }

    private SearchResult rootSearch(int[][] cells, int depth, int cpuPlayer) {
        List<Move> legal = boardService.getLegalMoves(cells, cpuPlayer);
        moveOrderingService.orderMoves(cells, legal, cpuPlayer);
        Move best = null;
        int bestScore = -INF;
        int alpha = -INF;
        int opponent = boardService.opponent(cpuPlayer);

        for (Move move : legal) {
            if (System.nanoTime() >= deadlineNanos)
                break;
            int[][] next = boardService.copyCells(cells);
            boardService.applyMove(next, move, cpuPlayer);
            int score = alphabeta(next, depth - 1, alpha, INF, opponent, true, true);
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
            alpha = Math.max(alpha, bestScore);
        }
        return new SearchResult(best, bestScore, depth, 0);
    }

    private int alphabeta(int[][] cells, int depth, int alpha, int beta, int player, boolean ultimate,
            boolean useTime) {
        if (useTime && System.nanoTime() >= deadlineNanos)
            return evaluate(cells, ultimate);
        boolean terminal = boardService.isBoardFull(cells) ||
                (boardService.getLegalMoves(cells, Board.BLACK).isEmpty()
                        && boardService.getLegalMoves(cells, Board.WHITE).isEmpty());
        if (terminal)
            return terminalScore(cells);
        if (depth <= 0)
            return evaluate(cells, ultimate);

        if (ultimate) {
            String key = transpositionTable.key(cells, player, depth) + ":" + rootCpuPlayer;
            TranspositionTable.Entry entry = transpositionTable.get(key);
            if (entry != null && entry.depth() >= depth)
                return entry.value();
        }

        List<Move> moves = boardService.getLegalMoves(cells, player);
        if (moves.isEmpty()) {
            int value = alphabeta(cells, depth - 1, alpha, beta, boardService.opponent(player), ultimate, useTime);
            if (ultimate)
                transpositionTable.put(transpositionTable.key(cells, player, depth) + ":" + rootCpuPlayer, depth,
                        value);
            return value;
        }

        moveOrderingService.orderMoves(cells, moves, player);
        int value;
        if (player == rootCpuPlayer) {
            value = -INF;
            for (Move move : moves) {
                int[][] next = boardService.copyCells(cells);
                boardService.applyMove(next, move, player);
                value = Math.max(value,
                        alphabeta(next, depth - 1, alpha, beta, boardService.opponent(player), ultimate, useTime));
                alpha = Math.max(alpha, value);
                if (alpha >= beta)
                    break;
            }
        } else {
            value = INF;
            for (Move move : moves) {
                int[][] next = boardService.copyCells(cells);
                boardService.applyMove(next, move, player);
                value = Math.min(value,
                        alphabeta(next, depth - 1, alpha, beta, boardService.opponent(player), ultimate, useTime));
                beta = Math.min(beta, value);
                if (alpha >= beta)
                    break;
            }
        }

        if (ultimate)
            transpositionTable.put(transpositionTable.key(cells, player, depth) + ":" + rootCpuPlayer, depth, value);
        return value;
    }

    private int evaluate(int[][] cells, boolean ultimate) {
        int score = 0;
        int opponent = boardService.opponent(rootCpuPlayer);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (cells[r][c] == rootCpuPlayer)
                    score += WEIGHTS[r][c];
                if (cells[r][c] == opponent)
                    score -= WEIGHTS[r][c];
            }
        }

        int empty = boardService.countEmpty(cells);
        int discDiff = discDiffForPlayer(cells, rootCpuPlayer);
        int discWeight = empty > 44 ? 1 : (empty > 16 ? 3 : 10);
        score += discDiff * discWeight;
        score += (boardService.getLegalMoves(cells, rootCpuPlayer).size()
                - boardService.getLegalMoves(cells, opponent).size()) * (ultimate ? 12 : 8);
        score += cornerDiff(cells, rootCpuPlayer) * (ultimate ? 300 : 100);
        score += dangerDiff(cells, rootCpuPlayer) * (ultimate ? 1 : 1) / (ultimate ? 1 : 2);
        if (ultimate)
            score += stableEdgeDiff(cells, rootCpuPlayer) * 20;
        return score;
    }

    private int terminalScore(int[][] cells) {
        int diff = discDiffForPlayer(cells, rootCpuPlayer);
        if (diff > 0)
            return 10000 + diff;
        if (diff < 0)
            return -10000 + diff;
        return 0;
    }

    private int discDiffForPlayer(int[][] cells, int player) {
        int playerCount = 0;
        int opponentCount = 0;
        int opponent = boardService.opponent(player);
        for (int[] row : cells) {
            for (int value : row) {
                if (value == player)
                    playerCount++;
                if (value == opponent)
                    opponentCount++;
            }
        }
        return playerCount - opponentCount;
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
                score -= 120;
            if (cells[p[0]][p[1]] == opponent)
                score += 120;
        }
        for (int[] p : cs) {
            if (cells[p[0]][p[1]] == player)
                score -= 80;
            if (cells[p[0]][p[1]] == opponent)
                score += 80;
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
}
