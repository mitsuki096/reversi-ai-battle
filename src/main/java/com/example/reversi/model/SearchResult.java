package com.example.reversi.model;

public class SearchResult {
    private Move bestMove;
    private int evaluation;
    private int depth;
    private long thinkingTimeMillis;

    public SearchResult() {
    }

    public SearchResult(Move bestMove, int evaluation, int depth, long thinkingTimeMillis) {
        this.bestMove = bestMove;
        this.evaluation = evaluation;
        this.depth = depth;
        this.thinkingTimeMillis = thinkingTimeMillis;
    }

    public Move getBestMove() {
        return bestMove;
    }

    public void setBestMove(Move bestMove) {
        this.bestMove = bestMove;
    }

    public int getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(int evaluation) {
        this.evaluation = evaluation;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public long getThinkingTimeMillis() {
        return thinkingTimeMillis;
    }

    public void setThinkingTimeMillis(long thinkingTimeMillis) {
        this.thinkingTimeMillis = thinkingTimeMillis;
    }
}
