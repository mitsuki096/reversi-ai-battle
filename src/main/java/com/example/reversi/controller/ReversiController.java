package com.example.reversi.controller;

import com.example.reversi.model.Board;
import com.example.reversi.model.Difficulty;
import com.example.reversi.service.GameService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ReversiController {
    private static final String SESSION_BOARD = "board";
    private static final String SESSION_SHOW_RECOMMENDATION = "showRecommendation";
    private static final String SESSION_UNDO_HISTORY = "undoHistory";
    private static final int MAX_UNDO_HISTORY = 60;

    private final GameService gameService;

    public ReversiController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/")
    public String showGame(HttpSession session, Model model) {
        Board board = getOrCreateBoard(session);
        gameService.autoPassIfNeeded(board);
        boolean showRecommendation = getShowRecommendation(session);
        boolean recommendationVisible = recommendationVisible(board, showRecommendation, false);

        model.addAttribute("board", board);
        model.addAttribute("difficulties", Difficulty.values());
        model.addAttribute("legalMoves", gameService.legalMoveKeys(board));
        model.addAttribute("showRecommendation", showRecommendation);
        model.addAttribute("recommendedMove", recommendationVisible ? gameService.recommendedMoveKey(board) : "");
        model.addAttribute("canUndo", canUndo(session));
        return "index";
    }

    @PostMapping("/move")
    public String move(@RequestParam int row, @RequestParam int col, HttpSession session) {
        Board board = getOrCreateBoard(session);
        saveUndoSnapshotIfLegal(session, board, row, col);
        gameService.playerMove(board, row, col);
        session.setAttribute(SESSION_BOARD, board);
        return "redirect:/";
    }

    @PostMapping("/undo")
    public String undo(HttpSession session) {
        undoLastMove(session);
        return "redirect:/";
    }

    @PostMapping("/reset")
    public String reset(HttpSession session) {
        Board old = (Board) session.getAttribute(SESSION_BOARD);
        Difficulty difficulty = old == null ? Difficulty.NORMAL : old.getDifficulty();
        boolean localTwoPlayers = old != null && old.isLocalTwoPlayers();
        clearUndoHistory(session);
        session.setAttribute(SESSION_BOARD, gameService.newGame(difficulty, localTwoPlayers));
        return "redirect:/";
    }

    @PostMapping("/difficulty")
    public String difficulty(@RequestParam String difficulty, HttpSession session) {
        Difficulty d = Difficulty.from(difficulty);
        Board old = (Board) session.getAttribute(SESSION_BOARD);
        boolean localTwoPlayers = old != null && old.isLocalTwoPlayers();
        clearUndoHistory(session);
        session.setAttribute(SESSION_BOARD, gameService.newGame(d, localTwoPlayers));
        return "redirect:/";
    }

    @PostMapping("/mode")
    public String mode(@RequestParam String mode, HttpSession session) {
        Board old = (Board) session.getAttribute(SESSION_BOARD);
        Difficulty difficulty = old == null ? Difficulty.NORMAL : old.getDifficulty();
        boolean localTwoPlayers = "LOCAL".equalsIgnoreCase(mode);
        clearUndoHistory(session);
        session.setAttribute(SESSION_BOARD, gameService.newGame(difficulty, localTwoPlayers));
        return "redirect:/";
    }

    @PostMapping("/recommendation")
    public String recommendation(@RequestParam(defaultValue = "false") boolean enabled, HttpSession session) {
        session.setAttribute(SESSION_SHOW_RECOMMENDATION, enabled);
        return "redirect:/";
    }

    @PostMapping("/api/player-move")
    @ResponseBody
    public Map<String, Object> apiPlayerMove(@RequestParam int row, @RequestParam int col, HttpSession session) {
        Board board = getOrCreateBoard(session);
        saveUndoSnapshotIfLegal(session, board, row, col);
        boolean cpuPending = gameService.playerMoveOnly(board, row, col);
        session.setAttribute(SESSION_BOARD, board);
        return boardResponse(board, getShowRecommendation(session), cpuPending, canUndo(session));
    }

    @PostMapping("/api/cpu-move")
    @ResponseBody
    public Map<String, Object> apiCpuMove(HttpSession session) {
        Board board = getOrCreateBoard(session);
        gameService.cpuMove(board);
        session.setAttribute(SESSION_BOARD, board);
        return boardResponse(board, getShowRecommendation(session), false, canUndo(session));
    }

    @PostMapping("/api/recommendation")
    @ResponseBody
    public Map<String, Object> apiRecommendation(@RequestParam(defaultValue = "false") boolean enabled,
            HttpSession session) {
        session.setAttribute(SESSION_SHOW_RECOMMENDATION, enabled);
        Board board = getOrCreateBoard(session);
        return boardResponse(board, enabled, false, canUndo(session));
    }

    @PostMapping("/api/reset")
    @ResponseBody
    public Map<String, Object> apiReset(HttpSession session) {
        Board old = (Board) session.getAttribute(SESSION_BOARD);
        Difficulty difficulty = old == null ? Difficulty.NORMAL : old.getDifficulty();
        boolean localTwoPlayers = old != null && old.isLocalTwoPlayers();

        clearUndoHistory(session);
        Board board = gameService.newGame(difficulty, localTwoPlayers);
        session.setAttribute(SESSION_BOARD, board);

        return boardResponse(board, getShowRecommendation(session), false, false);
    }

    @PostMapping("/api/undo")
    @ResponseBody
    public Map<String, Object> apiUndo(HttpSession session) {
        Board board = undoLastMove(session);
        return boardResponse(board, getShowRecommendation(session), false, canUndo(session));
    }

    private Board getOrCreateBoard(HttpSession session) {
        Board board = (Board) session.getAttribute(SESSION_BOARD);
        if (board == null) {
            clearUndoHistory(session);
            board = gameService.newGame(Difficulty.NORMAL, false);
            session.setAttribute(SESSION_BOARD, board);
        }
        return board;
    }

    private boolean getShowRecommendation(HttpSession session) {
        Boolean showRecommendation = (Boolean) session.getAttribute(SESSION_SHOW_RECOMMENDATION);
        if (showRecommendation == null) {
            showRecommendation = false;
            session.setAttribute(SESSION_SHOW_RECOMMENDATION, false);
        }
        return showRecommendation;
    }

    private boolean recommendationVisible(Board board, boolean showRecommendation, boolean cpuPending) {
        return showRecommendation
                && !cpuPending
                && board != null
                && !board.isGameOver()
                && !board.isLocalTwoPlayers()
                && board.getCurrentTurn() == board.getUserColor();
    }

    private Map<String, Object> boardResponse(Board board, boolean showRecommendation, boolean cpuPending,
            boolean canUndo) {
        boolean recommendationVisible = recommendationVisible(board, showRecommendation, cpuPending);
        Map<String, Object> res = new HashMap<>();
        res.put("cells", board.getCells());
        res.put("message", board.getMessage());
        res.put("gameOver", board.isGameOver());
        res.put("winner", board.getWinner());
        res.put("lastMoveRow", board.getLastMoveRow());
        res.put("lastMoveCol", board.getLastMoveCol());
        res.put("blackCount", board.getBlackCount());
        res.put("whiteCount", board.getWhiteCount());
        res.put("cpuThinkingTimeMillis", board.getCpuThinkingTimeMillis());
        res.put("cpuEvaluation", board.getCpuEvaluation());
        res.put("searchDepth", board.getSearchDepth());
        res.put("userColor", board.getUserColor());
        res.put("cpuColor", board.getCpuColor());
        res.put("userColorLabel", board.getUserColorLabel());
        res.put("cpuColorLabel", board.getCpuColorLabel());
        res.put("currentTurn", board.getCurrentTurn());
        res.put("currentTurnLabel", board.getCurrentTurnLabel());
        res.put("currentTurnLabelJa", board.getCurrentTurnLabelJa());
        res.put("localTwoPlayers", board.isLocalTwoPlayers());
        res.put("legalMoves", gameService.legalMoveKeys(board));
        res.put("showRecommendation", showRecommendation);
        res.put("recommendedMove", recommendationVisible ? gameService.recommendedMoveKey(board) : "");
        res.put("recommendationVisible", recommendationVisible);
        res.put("cpuPending", cpuPending);
        res.put("canUndo", canUndo);
        return res;
    }

    private void saveUndoSnapshotIfLegal(HttpSession session, Board board, int row, int col) {
        if (!gameService.canPlayerMove(board, row, col))
            return;
        Deque<Board> history = getUndoHistory(session);
        if (history.size() >= MAX_UNDO_HISTORY)
            history.removeLast();
        history.push(board.copy());
    }

    private Board undoLastMove(HttpSession session) {
        Deque<Board> history = getUndoHistory(session);
        if (history.isEmpty())
            return getOrCreateBoard(session);

        Board board = history.pop();
        if (board.isLocalTwoPlayers()) {
            board.setMessage("直前の着手を取り消しました。" + board.getCurrentTurnLabelJa() + "の番です。");
        } else {
            board.setMessage("直前の着手を取り消しました。あなたの番です。");
        }
        session.setAttribute(SESSION_BOARD, board);
        return board;
    }

    private boolean canUndo(HttpSession session) {
        return !getUndoHistory(session).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Deque<Board> getUndoHistory(HttpSession session) {
        Object value = session.getAttribute(SESSION_UNDO_HISTORY);
        if (value instanceof Deque<?>)
            return (Deque<Board>) value;
        Deque<Board> history = new ArrayDeque<>();
        session.setAttribute(SESSION_UNDO_HISTORY, history);
        return history;
    }

    private void clearUndoHistory(HttpSession session) {
        session.removeAttribute(SESSION_UNDO_HISTORY);
    }
}
