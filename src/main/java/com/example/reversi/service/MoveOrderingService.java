package com.example.reversi.service;

import com.example.reversi.model.Move;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class MoveOrderingService {
    private final BoardService boardService;
    public MoveOrderingService(BoardService boardService) { this.boardService = boardService; }

    public List<Move> orderMoves(int[][] cells, List<Move> moves, int player) {
        moves.sort(Comparator.comparingInt((Move m) -> score(cells, m, player)).reversed());
        return moves;
    }

    private int score(int[][] cells, Move m, int player) {
        int r = m.getRow(), c = m.getCol();
        int s = 0;
        if ((r == 0 || r == 7) && (c == 0 || c == 7)) s += 10000;
        else if (isXSq(r, c)) s -= 2000;
        else if (isCSq(r, c)) s -= 1000;
        else if (r == 0 || r == 7 || c == 0 || c == 7) s += 800;
        int[][] copy = boardService.copyCells(cells);
        s += boardService.applyMove(copy, m, player) * 10;
        s -= boardService.getLegalMoves(copy, boardService.opponent(player)).size() * 5;
        return s;
    }

    private boolean isXSq(int r, int c) {
        return (r == 1 && c == 1) || (r == 1 && c == 6) || (r == 6 && c == 1) || (r == 6 && c == 6);
    }
    private boolean isCSq(int r, int c) {
        return (r == 0 && c == 1) || (r == 1 && c == 0) || (r == 0 && c == 6) || (r == 1 && c == 7) ||
                (r == 6 && c == 0) || (r == 7 && c == 1) || (r == 6 && c == 7) || (r == 7 && c == 6);
    }
}
