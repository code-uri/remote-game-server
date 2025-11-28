package com.example.gameserver.model;

/**
 * Model representing a game in the remote game server.
 */
public class Game {

    private String id;
    private String name;
    private String status;
    private int playerCount;
    private int maxPlayers;

    public Game() {
    }

    public Game(String id, String name, String status, int playerCount, int maxPlayers) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}
