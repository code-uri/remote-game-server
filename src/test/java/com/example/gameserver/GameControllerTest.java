package com.example.gameserver;

import com.example.gameserver.controller.GameController;
import com.example.gameserver.model.Game;
import com.example.gameserver.model.JoinGameResult;
import com.example.gameserver.model.Player;
import com.example.gameserver.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for GameController using Spring Web MVC.
 */
@WebMvcTest(GameController.class)
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameService gameService;

    private Game testGame;
    private Player testPlayer;

    @BeforeEach
    void setUp() {
        testGame = new Game("game-1", "Test Game", "WAITING", 2, 4);
        testPlayer = new Player("player-1", "TestPlayer", 0, "CONNECTED");
    }

    @Test
    void getAllGames_shouldReturnListOfGames() throws Exception {
        List<Game> games = Arrays.asList(
            testGame,
            new Game("game-2", "Another Game", "IN_PROGRESS", 4, 8)
        );
        when(gameService.getAllGames()).thenReturn(games);

        mockMvc.perform(get("/api/games"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Test Game"));
    }

    @Test
    void getGameById_whenGameExists_shouldReturnGame() throws Exception {
        when(gameService.getGameById("game-1")).thenReturn(testGame);

        mockMvc.perform(get("/api/games/game-1"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("game-1"))
            .andExpect(jsonPath("$.name").value("Test Game"));
    }

    @Test
    void getGameById_whenGameNotExists_shouldReturn404() throws Exception {
        when(gameService.getGameById("nonexistent")).thenReturn(null);

        mockMvc.perform(get("/api/games/nonexistent"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createGame_shouldReturnCreatedGame() throws Exception {
        when(gameService.createGame(any(Game.class))).thenReturn(testGame);

        mockMvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Test Game\", \"maxPlayers\": 4}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test Game"));
    }

    @Test
    void updateGame_whenGameExists_shouldReturnUpdatedGame() throws Exception {
        Game updatedGame = new Game("game-1", "Updated Game", "WAITING", 2, 4);
        when(gameService.updateGame(eq("game-1"), any(Game.class))).thenReturn(updatedGame);

        mockMvc.perform(put("/api/games/game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Updated Game\", \"maxPlayers\": 4}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Game"));
    }

    @Test
    void deleteGame_whenGameExists_shouldReturn204() throws Exception {
        when(gameService.deleteGame("game-1")).thenReturn(true);

        mockMvc.perform(delete("/api/games/game-1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void joinGame_shouldReturnPlayer() throws Exception {
        when(gameService.joinGame(eq("game-1"), any(Player.class)))
            .thenReturn(JoinGameResult.success(testPlayer));

        mockMvc.perform(post("/api/games/game-1/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"TestPlayer\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("TestPlayer"));
    }

    @Test
    void joinGame_whenGameNotFound_shouldReturn404() throws Exception {
        when(gameService.joinGame(eq("nonexistent"), any(Player.class)))
            .thenReturn(JoinGameResult.gameNotFound());

        mockMvc.perform(post("/api/games/nonexistent/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"TestPlayer\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void joinGame_whenGameFull_shouldReturn409() throws Exception {
        when(gameService.joinGame(eq("game-1"), any(Player.class)))
            .thenReturn(JoinGameResult.gameFull());

        mockMvc.perform(post("/api/games/game-1/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"TestPlayer\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void getPlayersInGame_shouldReturnListOfPlayers() throws Exception {
        List<Player> players = Arrays.asList(testPlayer);
        when(gameService.getPlayersInGame("game-1")).thenReturn(players);

        mockMvc.perform(get("/api/games/game-1/players"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].name").value("TestPlayer"));
    }

    @Test
    void startGame_whenGameExists_shouldReturnGame() throws Exception {
        Game startedGame = new Game("game-1", "Test Game", "IN_PROGRESS", 2, 4);
        when(gameService.startGame("game-1")).thenReturn(startedGame);

        mockMvc.perform(post("/api/games/game-1/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void endGame_whenGameExists_shouldReturnGame() throws Exception {
        Game endedGame = new Game("game-1", "Test Game", "COMPLETED", 2, 4);
        when(gameService.endGame("game-1")).thenReturn(endedGame);

        mockMvc.perform(post("/api/games/game-1/end"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
