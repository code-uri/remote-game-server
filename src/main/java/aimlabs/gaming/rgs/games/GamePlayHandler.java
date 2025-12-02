package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.utils.ObjectMapperUtils;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.gamerounds.GameRoundService;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.playerbag.PlayerBagDocument;
import aimlabs.gaming.rgs.playerbag.PlayerBagStore;
import aimlabs.gaming.rgs.players.Player;
import aimlabs.gaming.rgs.players.PlayerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.aimlabs.gaming.engine.api.model.GamePlay;
import in.aimlabs.gaming.engine.api.model.GameType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static aimlabs.gaming.rgs.core.utils.ObjectMapperUtils.convertToJsonNode;
import static aimlabs.gaming.rgs.games.GamePlayResponse.GAME_PLAY;
import static aimlabs.gaming.rgs.games.GamePlayResponse.PLAYER_BAG;
import static aimlabs.gaming.rgs.games.GamePlayService.GAME_ROUND;
import static aimlabs.gaming.rgs.settings.GameSettingsService.isLockingPlayerRequired;
import static aimlabs.gaming.rgs.settings.GameSettingsService.isReadPlayerBag;

@Slf4j
@Component
public class GamePlayHandler implements GameHandler{

    private GameHandler nextHandler;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    PlayerService playerService;

    @Autowired
    PlayerBagStore playerBagStore;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    GameRoundService gameRoundService;

    @Autowired
    GamePlayService gamePlayService;

    @Override
    public void handle(JsonNode request, GamePlayContext gamePlayContext) {


        Pair<JsonNode, Optional<GameRound>> gamePlayNodeAndGameRound = fetchGamePlayAndGameRound(gamePlayContext.getGameSession(),
         gamePlayContext.getGameSkin(),
          request, gamePlayContext.getSettings());

        logPlayerBagBeforeGamePlay(gamePlayNodeAndGameRound.getFirst());
        String gameActivityUid = UUID.randomUUID().toString();
        GamePlayResponse gamePlayResponse = gamePlayService.proceedGamePlay(gamePlayContext, gamePlayNodeAndGameRound.getFirst());

        gamePlayResponse =  processGamePlayResponse(gamePlayContext.getGameSession(), gamePlayContext.getGameSkin(), gamePlayContext.getSettings(),
                gamePlayNodeAndGameRound.getSecond().orElse(null), gameActivityUid, gamePlayResponse);

        gamePlayContext.setEngineResponse(gamePlayResponse);
        this.nextHandler.handle(request, gamePlayContext);
        
    }

    @Override
    public void setNext(GameHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    private Pair<JsonNode, Optional<GameRound>> fetchGamePlayAndGameRound(GameSession gameSession,
                                                                         GameSkin gameSkin,
                                                                          JsonNode requestJsonNode,
                                                                           Map<String, Object> settings) {
        boolean continueRound = requestJsonNode.has("gameRound");
        boolean continueGamePlay = requestJsonNode.has("gamePlay");
        Pair<JsonNode, Optional<GameRound>> gamePlayNodeAndGameRound = null;

        if (continueRound || continueGamePlay) {
            gamePlayNodeAndGameRound = continueRoundScenario(gameSession, gameSkin, requestJsonNode, settings, continueRound, continueGamePlay);
        } else {
            gamePlayNodeAndGameRound = Pair.of(newRoundScenario(gameSession.getGameConfiguration(), gameSkin), Optional.empty());
        }

         readGamePlayAndPlayerBagData(gameSession,
                gameSkin,
                settings,
                gamePlayNodeAndGameRound.getFirst());

        return gamePlayNodeAndGameRound;
    }


    private GamePlayResponse processGamePlayResponse(GameSession gameSession,
                                                           GameSkin gameSkin,
                                                           Map<String, Object> settingsJsonNode,
                                                           GameRound gameRound,
                                                           String gameActivityUid,
                                                           GamePlayResponse gamePlayResponse) {

        String gameRoundUid = gameRound == null ? UUID.randomUUID().toString() : gameRound.getUid();
        gamePlayResponse.setGameActivityUid(gameActivityUid);
        gamePlayResponse.setGameRoundId(gameRoundUid);
        if (gameRound != null) {
            gamePlayResponse.setGameRound(gameRound);
            gamePlayResponse.setContinueRound(true);
        }

        JsonNode gamePlayJsonNodeDB = gamePlayService.saveGamePlayAndPushGameActivity(gamePlayResponse, gameSession);
        updatePlayerBagIfNeeded(gameSession, gameSkin, gamePlayResponse);
        return releaseLockIfNeeded(gameSession, gameSkin, settingsJsonNode, gamePlayResponse);
//                .flatMap(engineResponse -> handleGamePlayResponse(engineResponse, settingsJsonNode, gameSkin, gameSession, player));
    }

    private GamePlayResponse updatePlayerBagIfNeeded(GameSession gameSession, GameSkin gameSkin, GamePlayResponse gamePlayResponse) {
        JsonNode playerBag = gamePlayResponse.getPlayerBag();

        if (playerBag != null && playerBag.isObject()) {
            PlayerBagDocument playerBagDocument = playerBagStore.updateBag(gameSession.getTenant(), gameSession, gameSkin, ObjectMapperUtils.convertToMap(objectMapper, playerBag));
        }

        return  gamePlayResponse;
    }

    private GamePlayResponse releaseLockIfNeeded(GameSession gameSession, GameSkin gameSkin, Map<String, Object> settingsJsonNode, GamePlayResponse response) {
        if (isLockingPlayerRequired(settingsJsonNode)) {

            redisTemplate.delete(gameSession.getPlayer() + "-" + gameSkin.getUid());
        }

        return response;
    }




    public Player getPlayer(GameSession gameSession) {
        Player player;
        if (gameSession.isDemo()) {
            player = new Player();
            player.setUid(gameSession.getPlayer());
            player.setTenant(gameSession.getTenant());
            player.setBrand(gameSession.getBrand());
            player.setCorrelationId(gameSession.getPlayer());
        } else {
            player = playerService.findOneByUid(gameSession.getPlayer());
        }

        return player;
    }

    private void logPlayerBagBeforeGamePlay(JsonNode gamePlayJsonNode) {
        if (gamePlayJsonNode.has("data")) {
            JsonNode playerBagBefore = gamePlayJsonNode.get("data").get(PLAYER_BAG);
            //log.info("player bag before game play {}", playerBagBefore.toPrettyString());
        }
    }


    private JsonNode readGamePlayAndPlayerBagData(GameSession gameSession,
                                                        GameSkin gameSkin,
                                                        Map<String, Object> settings,
                                                        JsonNode gamePlayJsonNode) {
        JsonNode playerBag = readPlayerBagData(gameSession, gameSkin, settings);

        if(playerBag!=null){
            copyPlayerBagData(playerBag, gamePlayJsonNode);
        }
        return gamePlayJsonNode;
    }

    private JsonNode readPlayerBagData(GameSession gameSession, GameSkin gameSkin, Map<String, Object> settings) {

        boolean readPlayerBag = isReadPlayerBag(settings);
        log.info("Read Player Bag {}", readPlayerBag);
        if (readPlayerBag) {
            PlayerBagDocument playerBagDocument = playerBagStore.findOneByPlayerCurrencyGameAndSession(gameSession, gameSkin.getUid());

            //TODO 28 Aug
            // player.getData().put("playerBagUid", playerBagDocument.getId());
            return convertToJsonNode(objectMapper, playerBagDocument.getData());
        }
        return null;
    }

    private void copyPlayerBagData(JsonNode playerBag, JsonNode gamePlayJsonNode) {
        //    log.info("gameplayJsonNode {}", gamePlayJsonNode.toPrettyString());
        //  if (gamePlayJsonNode.has("data") && data.has(PLAYER_BAG)) {
        ((ObjectNode) gamePlayJsonNode).set("data", objectMapper.createObjectNode());
        ((ObjectNode) gamePlayJsonNode.get("data")).set(PLAYER_BAG, playerBag);
        // }
    }

    private JsonNode newRoundScenario(String gameConfiguration, GameSkin gameSkin) {
        return createGamePlayJsonNode(gameSkin, gameConfiguration);
    }

    private JsonNode createGamePlayJsonNode(GameSkin gameSkin, String gameConfiguration) {
        return objectMapper.valueToTree(
                GamePlay.startGame(
                        gameSkin.getUid(),
                        GameType.valueOf(gameSkin.getGameType()),
                        gameConfiguration
                )
        );
    }

    private Pair<JsonNode, Optional<GameRound>> continueRoundScenario(GameSession gameSession,
                                                                      GameSkin gameSkin,
                                                                      JsonNode requestJsonNode,
                                                                      Map<String, Object> settings,
                                                                      boolean continueRound,
                                                                      boolean continueGamePlay) {
        JsonNode gamePlayNode;
        if (continueGamePlay && !continueRound) {
            String gamePlayUid = requestJsonNode.get(GAME_PLAY).asText();
            gamePlayNode = gamePlayService.findGamePlay(gamePlayUid,
                    Status.INPROGRESS);

            return Pair.of(gamePlayNode, Optional.empty());

        } else {
            String gameRoundId = requestJsonNode.get(GAME_ROUND).asText();
            GameRound gameRound = gameRoundService.findOne(gameRoundId);

            gamePlayNode = gamePlayService.findGamePlay(gameRound.getGamePlay(),
                            Status.INPROGRESS);
            return Pair.of(gamePlayNode, Optional.of(gameRound));
        }
    }

}
