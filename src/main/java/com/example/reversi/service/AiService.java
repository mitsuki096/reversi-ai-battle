package com.example.reversi.service;

import com.example.reversi.model.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiService {
    private static final int INF = 1_000_000;
    private final BoardService boardService;
    private final EvaluationService evaluationService;
    private final MoveOrderingService moveOrderingService;
    private final TranspositionTable transpositionTable;
    private long deadlineNanos;

    public AiService(BoardService boardService, EvaluationService evaluationService,
                     MoveOrderingService moveOrderingService, TranspositionTable transpositionTable) {
        this.boardService = boardService;
        this.evaluationService = evaluationService;
        this.moveOrderingService = moveOrderingService;
        this.transpositionTable = transpositionTable;
    }

    public SearchResult chooseMove(int[][] cells, Difficulty difficulty) {
        long start = System.nanoTime();
        List<Move> legal = boardService.getLegalMoves(cells, Board.WHITE);
        if (legal.isEmpty()) return new SearchResult(null, evaluationService.evaluate(cells, difficulty == Difficulty.ULTIMATE), 0, 0);

        if (difficulty == Difficulty.ULTIMATE) {
            return chooseUltimate(cells, start);
        }

        int depth = difficulty.getDepth();
        moveOrderingService.orderMoves(cells, legal, Board.WHITE);
        Move best = legal.get(0);
        int bestScore = -INF;
        for (Move m : legal) {
            int[][] next = boardService.copyCells(cells);
            boardService.applyMove(next, m, Board.WHITE);
            int score = alphabeta(next, depth - 1, -INF, INF, Board.BLACK, false, false);
            if (score > bestScore) { bestScore = score; best = m; }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        return new SearchResult(best, bestScore, depth, ms);
    }

    private SearchResult chooseUltimate(int[][] cells, long start) {
        transpositionTable.clear();
        deadlineNanos = start + 3_000_000_000L;
        int empty = boardService.countEmpty(cells);
        int maxDepth = empty <= 12 ? empty + 2 : 10;
        Move best = null;
        int bestScore = -INF;
        int completedDepth = 0;
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (System.nanoTime() >= deadlineNanos) break;
            SearchResult r = rootSearch(cells, depth, true);
            if (System.nanoTime() < deadlineNanos && r.getBestMove() != null) {
                best = r.getBestMove();
                bestScore = r.getEvaluation();
                completedDepth = depth;
            }
        }
        if (best == null) {
            List<Move> legal = boardService.getLegalMoves(cells, Board.WHITE);
            moveOrderingService.orderMoves(cells, legal, Board.WHITE);
            best = legal.get(0);
            bestScore = evaluationService.evaluate(cells, true);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        return new SearchResult(best, bestScore, completedDepth, ms);
    }

    private SearchResult rootSearch(int[][] cells, int depth, boolean ultimate) {
        List<Move> legal = boardService.getLegalMoves(cells, Board.WHITE);
        moveOrderingService.orderMoves(cells, legal, Board.WHITE);
        Move best = null;
        int bestScore = -INF;
        int alpha = -INF;
        for (Move m : legal) {
            if (ultimate && System.nanoTime() >= deadlineNanos) break;
            int[][] next = boardService.copyCells(cells);
            boardService.applyMove(next, m, Board.WHITE);
            int score = alphabeta(next, depth - 1, alpha, INF, Board.BLACK, true, true);
            if (score > bestScore) { bestScore = score; best = m; }
            alpha = Math.max(alpha, bestScore);
        }
        return new SearchResult(best, bestScore, depth, 0);
    }

    private int alphabeta(int[][] cells, int depth, int alpha, int beta, int player, boolean ultimate, boolean useTime) {
        if (useTime && System.nanoTime() >= deadlineNanos) return evaluationService.evaluate(cells, ultimate);
        boolean terminal = boardService.isBoardFull(cells) ||
                (boardService.getLegalMoves(cells, Board.BLACK).isEmpty() && boardService.getLegalMoves(cells, Board.WHITE).isEmpty());
        if (depth <= 0 || terminal) return terminal ? evaluationService.terminalScore(cells) : evaluationService.evaluate(cells, ultimate);

        if (ultimate) {
            String key = transpositionTable.key(cells, player, depth);
            TranspositionTable.Entry entry = transpositionTable.get(key);
            if (entry != null && entry.depth() >= depth) return entry.value();
        }

        List<Move> moves = boardService.getLegalMoves(cells, player);
        if (moves.isEmpty()) {
            int val = alphabeta(cells, depth - 1, alpha, beta, boardService.opponent(player), ultimate, useTime);
            if (ultimate) transpositionTable.put(transpositionTable.key(cells, player, depth), depth, val);
            return val;
        }
        moveOrderingService.orderMoves(cells, moves, player);
        int value;
        if (player == Board.WHITE) {
            value = -INF;
            for (Move m : moves) {
                int[][] next = boardService.copyCells(cells);
                boardService.applyMove(next, m, player);
                value = Math.max(value, alphabeta(next, depth - 1, alpha, beta, Board.BLACK, ultimate, useTime));
                alpha = Math.max(alpha, value);
                if (alpha >= beta) break;
            }
        } else {
            value = INF;
            for (Move m : moves) {
                int[][] next = boardService.copyCells(cells);
                boardService.applyMove(next, m, player);
                value = Math.min(value, alphabeta(next, depth - 1, alpha, beta, Board.WHITE, ultimate, useTime));
                beta = Math.min(beta, value);
                if (alpha >= beta) break;
            }
        }
        if (ultimate) transpositionTable.put(transpositionTable.key(cells, player, depth), depth, value);
        return value;
    }
}
