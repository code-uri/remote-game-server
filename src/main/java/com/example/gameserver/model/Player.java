package com.example.gameserver.model;

/**
 * Model representing a player in the game.
 */
public class Player {

    private String id;
    private String name;
    private int score;
    private String status;

    public Player() {
    }

    public Player(String id, String name, int score, String status) {
        this.id = id;
        this.name = name;
        this.score = score;
        this.status = status;
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

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
