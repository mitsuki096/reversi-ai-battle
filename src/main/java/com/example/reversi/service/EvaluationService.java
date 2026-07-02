package com.example.reversi.service;

import com.example.reversi.model.Board;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {
    private final BoardService boardService;
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

    public EvaluationService(BoardService boardService) {
        this.boardService = boardService;
    }

    public int evaluate(int[][] cells, boolean ultimate) {
        if (isTerminal(cells))
            return terminalScore(cells);
        int score = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (cells[r][c] == Board.WHITE)
                    score += WEIGHTS[r][c];
                if (cells[r][c] == Board.BLACK)
                    score -= WEIGHTS[r][c];
            }
        }
        int empty = boardService.countEmpty(cells);
        int discWeight = empty > 44 ? 1 : (empty > 16 ? 3 : 10);
        score += boardService.discDiffForWhite(cells) * discWeight;

        int mobility = boardService.getLegalMoves(cells, Board.WHITE).size()
                - boardService.getLegalMoves(cells, Board.BLACK).size();
        score += mobility * (ultimate ? 12 : 8);

        if (ultimate) {
            score += cornerScore(cells) * 300;
            score += dangerScore(cells);
            score += stableEdgeScore(cells) * 20;
        } else {
            score += cornerScore(cells) * 100;
            score += dangerScore(cells) / 2;
        }
        return score;
    }

    private boolean isTerminal(int[][] cells) {
        return boardService.isBoardFull(cells) ||
                (boardService.getLegalMoves(cells, Board.BLACK).isEmpty()
                        && boardService.getLegalMoves(cells, Board.WHITE).isEmpty());
    }

    public int terminalScore(int[][] cells) {
        int diff = boardService.discDiffForWhite(cells);
        if (diff > 0)
            return 10000 + diff;
        if (diff < 0)
            return -10000 + diff;
        return 0;
    }

    private int cornerScore(int[][] cells) {
        int score = 0;
        int[][] cs = { { 0, 0 }, { 0, 7 }, { 7, 0 }, { 7, 7 } };
        for (int[] p : cs) {
            if (cells[p[0]][p[1]] == Board.WHITE)
                score++;
            if (cells[p[0]][p[1]] == Board.BLACK)
                score--;
        }
        return score;
    }

    private int dangerScore(int[][] cells) {
        int score = 0;
        int[][] xSquares = { { 1, 1 }, { 1, 6 }, { 6, 1 }, { 6, 6 } };
        int[][] cSquares = { { 0, 1 }, { 1, 0 }, { 0, 6 }, { 1, 7 }, { 6, 0 }, { 7, 1 }, { 6, 7 }, { 7, 6 } };
        for (int[] p : xSquares) {
            if (cells[p[0]][p[1]] == Board.WHITE)
                score -= 120;
            if (cells[p[0]][p[1]] == Board.BLACK)
                score += 120;
        }
        for (int[] p : cSquares) {
            if (cells[p[0]][p[1]] == Board.WHITE)
                score -= 80;
            if (cells[p[0]][p[1]] == Board.BLACK)
                score += 80;
        }
        return score;
    }

    private int stableEdgeScore(int[][] cells) {
        int score = 0;
        // 角から連続した辺石を簡易的な確定石として評価する
        score += stableFromCorner(cells, 0, 0, 0, 1) + stableFromCorner(cells, 0, 0, 1, 0);
        score += stableFromCorner(cells, 0, 7, 0, -1) + stableFromCorner(cells, 0, 7, 1, 0);
        score += stableFromCorner(cells, 7, 0, 0, 1) + stableFromCorner(cells, 7, 0, -1, 0);
        score += stableFromCorner(cells, 7, 7, 0, -1) + stableFromCorner(cells, 7, 7, -1, 0);
        return score;
    }

    private int stableFromCorner(int[][] cells, int r, int c, int dr, int dc) {
        int owner = cells[r][c];
        if (owner == Board.EMPTY)
            return 0;
        int score = 0;
        while (r >= 0 && r < 8 && c >= 0 && c < 8 && cells[r][c] == owner) {
            score += owner == Board.WHITE ? 1 : -1;
            r += dr;
            c += dc;
        }
        return score;
    }
}
