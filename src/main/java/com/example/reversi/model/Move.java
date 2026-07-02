package com.example.reversi.model;

import java.io.Serializable;
import java.util.Objects;

public class Move implements Serializable {
    private int row;
    private int col;

    public Move() {}
    public Move(int row, int col) { this.row = row; this.col = col; }

    public int getRow() { return row; }
    public void setRow(int row) { this.row = row; }
    public int getCol() { return col; }
    public void setCol(int col) { this.col = col; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Move move)) return false;
        return row == move.row && col == move.col;
    }
    @Override public int hashCode() { return Objects.hash(row, col); }
    @Override public String toString() { return "(" + row + "," + col + ")"; }
}
