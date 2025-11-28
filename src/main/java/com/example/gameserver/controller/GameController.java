package com.example.gameserver.controller;

import com.example.gameserver.model.Game;
import com.example.gameserver.model.JoinGameResult;
import com.example.gameserver.model.Player;
import com.example.gameserver.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for the Remote Game Server using Spring Web MVC.
 * 
 * This is the Spring Web API equivalent of a Spring WebFlux controller.
 * 
 * Key differences from Spring WebFlux:
 * 
 * WebFlux version would use:
 * - Mono<T> for single value responses
 * - Flux<T> for multiple value (stream) responses
 * - Non-blocking, reactive programming model
 * 
 * Spring Web MVC (this implementation) uses:
 * - Regular objects (T) for single value responses
 * - List<T> or Collection<T> for multiple values
 * - Traditional blocking, servlet-based model
 * 
 * Example WebFlux equivalent methods:
 * 
 *   @GetMapping
 *   public Flux<Game> getAllGames() {
 *       return gameService.getAllGames();
 *   }
 * 
 *   @GetMapping("/{id}")
 *   public Mono<ResponseEntity<Game>> getGameById(@PathVariable String id) {
 *       return gameService.getGameById(id)
 *           .map(game -> ResponseEntity.ok(game))
 *           .defaultIfEmpty(ResponseEntity.notFound().build());
 *   }
 * 
 *   @PostMapping
 *   public Mono<Game> createGame(@RequestBody Game game) {
 *       return gameService.createGame(game);
 *   }
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /**
     * Get all games.
     * WebFlux equivalent: Flux<Game> getAllGames()
     */
    @GetMapping
    public ResponseEntity<List<Game>> getAllGames() {
        List<Game> games = gameService.getAllGames();
        return ResponseEntity.ok(games);
    }

    /**
     * Get a specific game by ID.
     * WebFlux equivalent: Mono<ResponseEntity<Game>> getGameById(String id)
     */
    @GetMapping("/{id}")
    public ResponseEntity<Game> getGameById(@PathVariable String id) {
        Game game = gameService.getGameById(id);
        if (game != null) {
            return ResponseEntity.ok(game);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Create a new game.
     * WebFlux equivalent: Mono<Game> createGame(Game game)
     */
    @PostMapping
    public ResponseEntity<Game> createGame(@RequestBody Game game) {
        Game createdGame = gameService.createGame(game);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdGame);
    }

    /**
     * Update an existing game.
     * WebFlux equivalent: Mono<ResponseEntity<Game>> updateGame(String id, Game game)
     */
    @PutMapping("/{id}")
    public ResponseEntity<Game> updateGame(@PathVariable String id, @RequestBody Game game) {
        Game updatedGame = gameService.updateGame(id, game);
        if (updatedGame != null) {
            return ResponseEntity.ok(updatedGame);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Delete a game.
     * WebFlux equivalent: Mono<ResponseEntity<Void>> deleteGame(String id)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGame(@PathVariable String id) {
        if (gameService.deleteGame(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Join a game as a player.
     * WebFlux equivalent: Mono<ResponseEntity<Player>> joinGame(String gameId, Player player)
     * Returns 201 Created on success, 404 Not Found if game doesn't exist, 409 Conflict if game is full.
     */
    @PostMapping("/{gameId}/join")
    public ResponseEntity<Player> joinGame(@PathVariable String gameId, @RequestBody Player player) {
        JoinGameResult result = gameService.joinGame(gameId, player);
        switch (result.getStatus()) {
            case SUCCESS:
                return ResponseEntity.status(HttpStatus.CREATED).body(result.getPlayer());
            case GAME_NOT_FOUND:
                return ResponseEntity.notFound().build();
            case GAME_FULL:
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            default:
                return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all players in a game.
     * WebFlux equivalent: Flux<Player> getPlayersInGame(String gameId)
     */
    @GetMapping("/{gameId}/players")
    public ResponseEntity<List<Player>> getPlayersInGame(@PathVariable String gameId) {
        List<Player> players = gameService.getPlayersInGame(gameId);
        return ResponseEntity.ok(players);
    }

    /**
     * Start a game.
     * WebFlux equivalent: Mono<ResponseEntity<Game>> startGame(String gameId)
     */
    @PostMapping("/{gameId}/start")
    public ResponseEntity<Game> startGame(@PathVariable String gameId) {
        Game game = gameService.startGame(gameId);
        if (game != null) {
            return ResponseEntity.ok(game);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * End a game.
     * WebFlux equivalent: Mono<ResponseEntity<Game>> endGame(String gameId)
     */
    @PostMapping("/{gameId}/end")
    public ResponseEntity<Game> endGame(@PathVariable String gameId) {
        Game game = gameService.endGame(gameId);
        if (game != null) {
            return ResponseEntity.ok(game);
        }
        return ResponseEntity.notFound().build();
    }
}
