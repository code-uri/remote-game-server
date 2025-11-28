package com.example.gameserver.model;

/**
 * Result object for join game operation.
 * Provides more specific information about the result of the operation.
 */
public class JoinGameResult {

    public enum Status {
        SUCCESS,
        GAME_NOT_FOUND,
        GAME_FULL
    }

    private final Status status;
    private final Player player;

    private JoinGameResult(Status status, Player player) {
        this.status = status;
        this.player = player;
    }

    public static JoinGameResult success(Player player) {
        return new JoinGameResult(Status.SUCCESS, player);
    }

    public static JoinGameResult gameNotFound() {
        return new JoinGameResult(Status.GAME_NOT_FOUND, null);
    }

    public static JoinGameResult gameFull() {
        return new JoinGameResult(Status.GAME_FULL, null);
    }

    public Status getStatus() {
        return status;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
