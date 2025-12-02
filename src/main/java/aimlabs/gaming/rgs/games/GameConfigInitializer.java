package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.gamerounds.GameRoundService;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.settings.GameSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class GameConfigInitializer implements GameInitializer {

    public static final String GAME_PLAY = "gamePlay";
    public static final String GAME_ACTIVITY = "gameActivity";
    public static final String TOTAL_WINNINGS = "totalWin";
    public static final String TOTAL_BET = "totalWager";
    public static final String STATUS = "status";
    public static final String UID = "uid";
    public static final String GAME_CLIENT_RESPONSE = "gameClientResponse";

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    GameRoundService gameRoundService;

    @Autowired
    GamePlayService gamePlayService;

    @Autowired
    GameEngineServiceAdaptor engineAdaptor;

    @Override
    public JsonNode loadData(GameSession gameSession, GameSkin gameSkin, Map<String,Object> settings) {
        ObjectNode config = engineAdaptor.getGameClientConfig(gameSkin.getGameConfiguration());
        config.setAll((ObjectNode) objectMapper.valueToTree(settings));
        config.set("urls", objectMapper.valueToTree(gameSession.getUrls()));

        if(gameSession.getJurisdiction() != null) {
            ObjectNode regulations = objectMapper.createObjectNode();
            regulations.put("jurisdiction", gameSession.getJurisdiction());
            regulations.put("realityCheckInterval", gameSession.getRealityCheckIntervalInMilliSeconds());
            regulations.put("elapsedTime", Instant.now().toEpochMilli() - gameSession.getElapsedTimeInMilliSeconds());
            config.set("regulations", regulations);
        }

        ArrayNode unfinishedGames = objectMapper.createArrayNode();
        boolean unfinishedGamesSupported = GameSettingsService.isUnfinishedGamesSupported(settings);
        
        if (unfinishedGamesSupported) {
            boolean confirmHandSupported = GameSettingsService.isConfirmHandSupportedFromMap(settings);
            unfinishedGames = findUnfinishedGames(gameSession, gameSkin, confirmHandSupported);
        }
        
        ObjectNode response = objectMapper.createObjectNode();
        response.set("config", config);
        response.set("unfinishedGames", unfinishedGames);
        return response;
    }

    public ArrayNode findUnfinishedGames(GameSession gameSession, GameSkin gameSkin, boolean confirmHand) {
        ArrayNode result = objectMapper.createArrayNode();
        
        try {
            // Get unfinished game rounds for this player and game
            var unfinishedGame = gameRoundService.getLastGameRoundWithDetails(
                gameSession.getPlayer(), 
                gameSkin.getUid(), 
                gameSession.getTenant(), 
                gameSession.getCurrency()
            );
            
            if (unfinishedGame != null && unfinishedGame.getGameRound() != null) {
                GameRound gameRound = unfinishedGame.getGameRound();
                
                if (gameRound.getStatus() == Status.INPROGRESS) {
                    Object engineResponse = gamePlayService.prepareGameClientResponse(gameSkin.getGameType().toLowerCase(),
                                        gameSession.getGameConfiguration(),
                                        unfinishedGame.getGamePlay(),
                                        unfinishedGame.getGameActivity());
                    
                    JsonNode jsonNode = unfinishedGameData(Optional.of(gameRound), 
                        objectMapper.valueToTree(engineResponse));
                    result.add(jsonNode);
                }
            }
        } catch (Exception e) {
            log.error("Failed to read unfinished gameRounds.", e);
        }
        
        return result;
    }

    public JsonNode unfinishedGameData(Optional<GameRound> gameRoundOptional, JsonNode engineResponse) {
        ObjectNode response = objectMapper.createObjectNode();
        if (gameRoundOptional.isPresent()) {
            GameRound gameRound = gameRoundOptional.get();
            response.put(UID, gameRound.getUid());
            if(gameRound.getFreeSpinsAllotmentId() != null)
                response.put("freeSpinsAllotmentId", gameRound.getFreeSpinsAllotmentId());
            response.put(STATUS, gameRound.getStatus().name());
            response.set(TOTAL_BET, objectMapper.valueToTree(gameRound.getTotalWager()));
            response.set(TOTAL_WINNINGS, objectMapper.valueToTree(gameRound.getTotalWin()));
        }

        if (engineResponse.get(GAME_CLIENT_RESPONSE) != null) {
            response.set(GAME_CLIENT_RESPONSE, engineResponse.get(GAME_CLIENT_RESPONSE));
        } else {
            if (engineResponse.has(GAME_PLAY))
                response.set(GAME_PLAY, engineResponse.get(GAME_PLAY));

            if (engineResponse.has(GAME_ACTIVITY))
                response.set(GAME_ACTIVITY, engineResponse.get(GAME_ACTIVITY));
        }
        return response;
    }

}
