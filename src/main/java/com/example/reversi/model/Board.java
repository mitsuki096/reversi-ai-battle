package com.example.reversi.model;

import java.io.Serializable;

public class Board implements Serializable {
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    private int[][] cells = new int[8][8];
    private boolean gameOver;
    private String message = "あなたの番です";
    private String winner = "";
    private int lastMoveRow = -1;
    private int lastMoveCol = -1;
    private int blackCount;
    private int whiteCount;
    private Difficulty difficulty = Difficulty.NORMAL;
    private long cpuThinkingTimeMillis;
    private int cpuEvaluation;
    private int searchDepth;
    private int userColor = BLACK;
    private int cpuColor = WHITE;
    private int currentTurn = BLACK;
    private boolean localTwoPlayers;

    public int[][] getCells() {
        return cells;
    }

    public void setCells(int[][] cells) {
        this.cells = cells;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public int getLastMoveRow() {
        return lastMoveRow;
    }

    public void setLastMoveRow(int lastMoveRow) {
        this.lastMoveRow = lastMoveRow;
    }

    public int getLastMoveCol() {
        return lastMoveCol;
    }

    public void setLastMoveCol(int lastMoveCol) {
        this.lastMoveCol = lastMoveCol;
    }

    public int getBlackCount() {
        return blackCount;
    }

    public void setBlackCount(int blackCount) {
        this.blackCount = blackCount;
    }

    public int getWhiteCount() {
        return whiteCount;
    }

    public void setWhiteCount(int whiteCount) {
        this.whiteCount = whiteCount;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public long getCpuThinkingTimeMillis() {
        return cpuThinkingTimeMillis;
    }

    public void setCpuThinkingTimeMillis(long cpuThinkingTimeMillis) {
        this.cpuThinkingTimeMillis = cpuThinkingTimeMillis;
    }

    public int getCpuEvaluation() {
        return cpuEvaluation;
    }

    public void setCpuEvaluation(int cpuEvaluation) {
        this.cpuEvaluation = cpuEvaluation;
    }

    public int getSearchDepth() {
        return searchDepth;
    }

    public void setSearchDepth(int searchDepth) {
        this.searchDepth = searchDepth;
    }

    public int getUserColor() {
        return userColor;
    }

    public void setUserColor(int userColor) {
        this.userColor = userColor;
    }

    public int getCpuColor() {
        return cpuColor;
    }

    public void setCpuColor(int cpuColor) {
        this.cpuColor = cpuColor;
    }

    public int getCurrentTurn() {
        return currentTurn;
    }

    public void setCurrentTurn(int currentTurn) {
        this.currentTurn = currentTurn;
    }

    public boolean isLocalTwoPlayers() {
        return localTwoPlayers;
    }

    public void setLocalTwoPlayers(boolean localTwoPlayers) {
        this.localTwoPlayers = localTwoPlayers;
    }

    public String getUserColorLabel() {
        return userColor == BLACK ? "Black" : "White";
    }

    public String getCpuColorLabel() {
        return cpuColor == BLACK ? "Black" : "White";
    }

    public String getCurrentTurnLabel() {
        return currentTurn == BLACK ? "Black" : (currentTurn == WHITE ? "White" : "-");
    }

    public String getCurrentTurnLabelJa() {
        return currentTurn == BLACK ? "黒" : (currentTurn == WHITE ? "白" : "-");
    }
}
