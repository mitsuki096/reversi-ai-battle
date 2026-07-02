package com.example.reversi.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TranspositionTable {
    private final Map<String, Entry> table = new ConcurrentHashMap<>();

    public void clear() {
        table.clear();
    }

    public Entry get(String key) {
        return table.get(key);
    }

    public void put(String key, int depth, int value) {
        table.put(key, new Entry(depth, value));
    }

    public String key(int[][] cells, int player, int depth) {
        StringBuilder sb = new StringBuilder(70);
        for (int[] row : cells)
            for (int v : row)
                sb.append(v);
        sb.append(':').append(player).append(':').append(depth);
        return sb.toString();
    }

    public record Entry(int depth, int value) {
    }
}
