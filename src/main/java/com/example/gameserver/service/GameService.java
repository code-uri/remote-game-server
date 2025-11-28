package com.example.gameserver.service;

import com.example.gameserver.model.Game;
import com.example.gameserver.model.Player;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service class for game operations.
 * 
 * In Spring WebFlux, this service would return Mono<Game> or Flux<Game>.
 * In Spring Web MVC (this implementation), we return regular objects.
 * 
 * WebFlux equivalent:
 *   public Mono<Game> getGameById(String id)
 *   public Flux<Game> getAllGames()
 * 
 * Web MVC (current):
 *   public Game getGameById(String id)
 *   public List<Game> getAllGames()
 */
@Service
public class GameService {

    private final Map<String, Game> games = new ConcurrentHashMap<>();
    private final Map<String, List<Player>> gamePlayers = new ConcurrentHashMap<>();

    public GameService() {
        // Initialize with sample games
        String gameId1 = UUID.randomUUID().toString();
        String gameId2 = UUID.randomUUID().toString();
        games.put(gameId1, new Game(gameId1, "Battle Arena", "WAITING", 2, 4));
        games.put(gameId2, new Game(gameId2, "Racing Championship", "IN_PROGRESS", 4, 8));
        gamePlayers.put(gameId1, new ArrayList<>());
        gamePlayers.put(gameId2, new ArrayList<>());
    }

    public List<Game> getAllGames() {
        return new ArrayList<>(games.values());
    }

    public Game getGameById(String id) {
        return games.get(id);
    }

    public Game createGame(Game game) {
        String id = UUID.randomUUID().toString();
        game.setId(id);
        game.setStatus("WAITING");
        game.setPlayerCount(0);
        games.put(id, game);
        gamePlayers.put(id, new ArrayList<>());
        return game;
    }

    public Game updateGame(String id, Game game) {
        if (games.containsKey(id)) {
            game.setId(id);
            games.put(id, game);
            return game;
        }
        return null;
    }

    public boolean deleteGame(String id) {
        gamePlayers.remove(id);
        return games.remove(id) != null;
    }

    public Player joinGame(String gameId, Player player) {
        Game game = games.get(gameId);
        if (game == null) {
            return null;
        }
        
        if (game.getPlayerCount() >= game.getMaxPlayers()) {
            return null;
        }
        
        String playerId = UUID.randomUUID().toString();
        player.setId(playerId);
        player.setStatus("CONNECTED");
        player.setScore(0);
        
        gamePlayers.get(gameId).add(player);
        game.setPlayerCount(game.getPlayerCount() + 1);
        
        return player;
    }

    public List<Player> getPlayersInGame(String gameId) {
        return gamePlayers.getOrDefault(gameId, new ArrayList<>());
    }

    public Game startGame(String gameId) {
        Game game = games.get(gameId);
        if (game != null && "WAITING".equals(game.getStatus())) {
            game.setStatus("IN_PROGRESS");
        }
        return game;
    }

    public Game endGame(String gameId) {
        Game game = games.get(gameId);
        if (game != null) {
            game.setStatus("COMPLETED");
        }
        return game;
    }
}
