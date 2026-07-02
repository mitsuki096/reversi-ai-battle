package com.example.reversi.service;

import com.example.reversi.model.Board;
import com.example.reversi.model.Move;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BoardService {
    private static final int[][] DIRS = {
            {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };

    public Board createInitialBoard() {
        Board board = new Board();
        int[][] c = board.getCells();
        c[3][3] = Board.WHITE;
        c[3][4] = Board.BLACK;
        c[4][3] = Board.BLACK;
        c[4][4] = Board.WHITE;
        updateCounts(board);
        return board;
    }

    public boolean isInside(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    public int opponent(int player) {
        return player == Board.BLACK ? Board.WHITE : Board.BLACK;
    }

    public boolean isLegalMove(int[][] cells, int row, int col, int player) {
        if (!isInside(row, col) || cells[row][col] != Board.EMPTY) return false;
        int opp = opponent(player);
        for (int[] d : DIRS) {
            int r = row + d[0], c = col + d[1];
            boolean foundOpponent = false;
            while (isInside(r, c) && cells[r][c] == opp) {
                foundOpponent = true;
                r += d[0]; c += d[1];
            }
            if (foundOpponent && isInside(r, c) && cells[r][c] == player) return true;
        }
        return false;
    }

    public List<Move> getLegalMoves(int[][] cells, int player) {
        List<Move> moves = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (isLegalMove(cells, r, c, player)) moves.add(new Move(r, c));
            }
        }
        return moves;
    }

    public int applyMove(int[][] cells, Move move, int player) {
        if (move == null) return 0;
        int row = move.getRow();
        int col = move.getCol();
        if (!isLegalMove(cells, row, col, player)) return 0;
        int flipped = 0;
        int opp = opponent(player);
        cells[row][col] = player;
        for (int[] d : DIRS) {
            List<int[]> line = new ArrayList<>();
            int r = row + d[0], c = col + d[1];
            while (isInside(r, c) && cells[r][c] == opp) {
                line.add(new int[]{r, c});
                r += d[0]; c += d[1];
            }
            if (!line.isEmpty() && isInside(r, c) && cells[r][c] == player) {
                for (int[] p : line) {
                    cells[p[0]][p[1]] = player;
                    flipped++;
                }
            }
        }
        return flipped;
    }

    public int[][] copyCells(int[][] src) {
        int[][] copy = new int[8][8];
        for (int i = 0; i < 8; i++) System.arraycopy(src[i], 0, copy[i], 0, 8);
        return copy;
    }

    public void updateCounts(Board board) {
        int black = 0, white = 0;
        for (int[] row : board.getCells()) {
            for (int v : row) {
                if (v == Board.BLACK) black++;
                if (v == Board.WHITE) white++;
            }
        }
        board.setBlackCount(black);
        board.setWhiteCount(white);
    }

    public int countEmpty(int[][] cells) {
        int n = 0;
        for (int[] row : cells) for (int v : row) if (v == Board.EMPTY) n++;
        return n;
    }

    public boolean isBoardFull(int[][] cells) {
        return countEmpty(cells) == 0;
    }

    public int discDiffForWhite(int[][] cells) {
        int black = 0, white = 0;
        for (int[] row : cells) for (int v : row) {
            if (v == Board.BLACK) black++;
            if (v == Board.WHITE) white++;
        }
        return white - black;
    }
}
