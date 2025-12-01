package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.brandgames.BrandGameAggregate;
import aimlabs.gaming.rgs.brandgames.IBrandGameService;
import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.core.dto.SearchRequest;
import aimlabs.gaming.rgs.core.dto.SearchResponse;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gameactivities.GameActivityStore;
import aimlabs.gaming.rgs.gameoperators.GameReplayRequest;
import aimlabs.gaming.rgs.gameplay.GamePlayDocument;
import aimlabs.gaming.rgs.gameplay.GamePlayStore;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.gamerounds.GameRoundStore;
import aimlabs.gaming.rgs.gamerounds.IGameRoundService;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gamesessions.GameSessionContext;
import aimlabs.gaming.rgs.gamesessions.GameSessionService;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.gameskins.IGameSkinService;
import aimlabs.gaming.rgs.gamesupplier.IGameSupplierService;
import aimlabs.gaming.rgs.players.Player;
import aimlabs.gaming.rgs.players.PlayerInfo;
import aimlabs.gaming.rgs.players.PlayerService;
import aimlabs.gaming.rgs.settings.GameSettingsService;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.DBObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static aimlabs.gaming.rgs.games.GamePlayResponse.GAME_CLIENT_RESPONSE;
import static aimlabs.gaming.rgs.settings.GameSettingsService.isForceUnfinished;
import static aimlabs.gaming.rgs.settings.GameSettingsService.isLockingPlayerRequired;

@Data
@Slf4j
@Component
public class GameRequestHandler {

    @Autowired
    GameSupplierLocator gameSupplierLocator;
    @Autowired
    GameSessionService gameSessionService;
    @Autowired
    PlayerService playerService;
    @Autowired
    GameSettingsService gameSettingService;
    @Autowired
    IBrandService brandService;
    @Autowired
    IGameSkinService gameSkinService;
    @Autowired
    IBrandGameService brandGameService;
    @Autowired
    IGameRoundService gameRoundService;
    @Autowired
    List<GameInitializer> gameInitializers;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    GamePlayStore gamePlayStore;
    @Autowired
    GameActivityStore gameActivityStore;
    @Autowired
    GameRoundStore gameRoundStore;
    @Autowired
    GamePlayService gamePlayService;
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @Autowired
    GameFlowPipeline gameFlowPipeline;
    @Autowired
    private GameEngineServiceAdaptor engineAdaptor;

    public URI launchGame(GameLaunchRequest launchRequest) {
        BrandGameAggregate brandGame = brandGameService.findOneByNetworkAndBrandAndGameId(null, launchRequest.getBrand(), launchRequest.getGameId());
        if (brandGame == null) {
            throw new BaseRuntimeException(SystemErrorCode.INACTIVE_GAME);
        }

        GameSession gameSession = new GameSession();
        gameSession.setToken(launchRequest.getToken());
        gameSession.setPamConnector(brandGame.brand().getConnectorUid());
        gameSession.setBrand(brandGame.brand().getUid());
        gameSession.setGame(brandGame.game().getUid());
        gameSession.setDemo(launchRequest.isDemo());
        gameSession.setGameConnector(brandGame.game().getConnector());

         return ScopedValue.where(
                GameSessionContext.GAME_SESSION, gameSession
        ).call(() -> getSupplier(brandGame.game().getConnector()).launchGame(launchRequest));
    }

    private IGameSupplierService getSupplier(String connectorUid) {
        return getGameSupplierLocator().getSupplier(connectorUid);
//                .getFirst().launchGame(launchRequest, tenantContextHolder);
    }

    public Pair<GameSession, JsonNode> initialiseGame(String token, String brandId, String gameId) {
        //get brand and gameskin
        boolean isDemoGame = token.toLowerCase().startsWith("demo");
        GameSession gameSession = gameSessionService.findOneByToken(token);
        BrandGameAggregate brandGame = brandGameService.findOneByNetworkAndBrandAndGameId(isDemoGame?"default":gameSession.getNetwork(),brandId, gameId);

        String tenant = brandGame.brand().getTenant();
        Brand brand = brandGame.brand();
        GameSkin gameSkin = brandGame.game();

        PlayerInfo playerInfo = initialisePlayer(token, null, null, brand, gameSkin);
        gameSession = gameSessionService.findOneByToken(playerInfo.getExternalToken());

        Boolean unfinishedGameRoundExists = gameRoundService.isUnfinishedGameRoundExists(playerInfo.getUid(), gameId);
        if (!unfinishedGameRoundExists && !brand.getStatus().equals(Status.ACTIVE)) {
            throw new BaseRuntimeException(SystemErrorCode.INACTIVE_GAME);
        }

        GamePlayContext ctx = new GamePlayContext(gameSession, brandGame.brand(), brandGame.game());


        
        //load player
        Player player = playerService.findOneByUid(playerInfo.getPlayer());

        Map<String, Object> settings = gameSettingService.findGameSettingsForCurrency(tenant,
                brandId,
                gameSkin.getUid(), playerInfo.getWallet().getCurrency());


        String gameConfiguration = (String) settings.getOrDefault("gameConfiguration", gameSkin.getGameConfiguration());

        if (gameSession != null) {
            if (!gameConfiguration.equals(gameSession.getGameConfiguration()))
                throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Invalid game session. Please re-launch the game.");

            if (gameSession.isDemo()) {
                gameSessionService.setExpiration(gameSession);
            } else
                gameSessionService.keepSessionAlive(gameSession);

        } else {

            GameSession session = new GameSession(tenant, brand.getUid(),
                    isDemoGame ? playerInfo.getExternalToken() : playerInfo.getUid(),
                    playerInfo.getWallet().getCurrency());
            session.setNetwork(brand.getNetwork());
            session.setGame(gameSkin.getUid());
            session.setDemo(isDemoGame);
            session.setProviderGame(gameSkin.getProviderGame());
            session.setToken(playerInfo.getExternalToken());
            session.setPlayerTags(playerInfo.getTags());
            session.setGameConfiguration(gameConfiguration);
            gameSession = gameSessionService.createGameSession(session);
        }

        if (gameSession == null) {
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "game session is null");
        }

        JsonNode response = getResponseJsonNode(gameSession, gameSkin, settings);

        ObjectNode walletNode = objectMapper.valueToTree(playerInfo.getWallet());
        walletNode.put("fractions",
                playerInfo.getWallet().getTotalAvailable().getCurrency()
                        .getDefaultFractionDigits());

        ObjectNode playerWallet = objectMapper.createObjectNode();
        playerWallet.set("wallet", walletNode);
        ((ObjectNode) response).set("playerInfo", playerWallet);

        return Pair.of(gameSession, response);
    }

    private JsonNode getResponseJsonNode(GameSession gameSession, GameSkin gameSkin, Map<String, Object> settings) {
        return gameInitializers.stream()
                .map(gameInitializer -> {
                    JsonNode as = gameInitializer.loadData(gameSession, gameSkin, settings);

                    if (as == null)
                        as = objectMapper.createObjectNode();

                    return as;
                })

                .reduce((jsonNode, jsonNode2) -> ((ObjectNode) jsonNode).setAll((ObjectNode) jsonNode2))
                .get();

    }


    PlayerInfo initialisePlayer(String token,
                                String playerId,
                                String currency,
                                Brand brand,
                                GameSkin gameSkin) {
        GameSession gameSession = gameSessionService.findOneByUid(token);

        if (gameSession != null) {
            if (gameSession.isDemo()) {
                return playerService.initialise(gameSession.getNetwork(),
                        gameSession.getToken(),
                        null,
                        null,
                        brand.getUid(),
                        gameSkin.getUid(),
                        true);
            }

            Player player = playerService.findOneByUid(gameSession.getPlayer());
            if (player == null) {
                throw new BaseRuntimeException(SystemErrorCode.PLAYER_NOT_FOUND);
            }
            return playerService.initialise(player.getNetwork(), token, player.getCorrelationId(), gameSession.getCurrency(),
                    gameSession.getBrand(), gameSkin.getUid(), true);
        } else {
            return playerService.initialise(brand.getNetwork(), token, playerId, currency,
                    brand.getUid(), gameSkin.getUid(), true);
        }

    }


    public JsonNode playGame(GameSession gameSession, JsonNode requestJsonNode) {

        BrandGameAggregate brandGame = brandGameService
                .findOneByNetworkAndBrandAndGameId(gameSession.getNetwork(),
                        gameSession.getBrand(),
                        requestJsonNode.get("gameId").asText());

        if (brandGame == null)
            throw new BaseRuntimeException(SystemErrorCode.GAME_COMING_SOON);

        //BrandGameAggregate brandGame = tuple2.getT1();
        boolean continueReq = requestJsonNode.has("gameRound");
        if (brandGame.status() == Status.INACTIVE && !continueReq) {
            throw new BaseRuntimeException(SystemErrorCode.GAME_COMING_SOON);
        }

        String gameActivityUid = UUID.randomUUID().toString();


        Map<String, Object> settings = gameSettingService.findGameSettingsForCurrency(gameSession.getTenant(),
                gameSession.getBrand(),
                brandGame.game().getUid(), gameSession.getCurrency());

        preChecks(gameSession, brandGame.game(), requestJsonNode, settings);

        Player player = getPlayer(gameSession);

        GamePlayContext gamePlayContext = new GamePlayContext();
        gamePlayContext.setSettings(settings);
        gamePlayContext.setPlayer(player);
        gamePlayContext.setGameSession(gameSession);
        gamePlayContext.setGameSkin(brandGame.game());

        gameFlowPipeline.handle(requestJsonNode, gamePlayContext);

        return composeGameResponse(gamePlayContext.getEngineResponse());
    }


    private void preChecks(GameSession gameSession, GameSkin gameSkin, JsonNode requestJsonNode, Map<String, Object> settings) {
        boolean continueReq = requestJsonNode.has("gameRound");
        boolean forceUnfinished = isForceUnfinished(settings);
        if (forceUnfinished && !continueReq) {
            // force unfinished game
            Boolean pendingRoundExists = gameRoundStore.isPendingRoundExists(gameSession.getPlayer(), gameSkin.getUid());
            if (pendingRoundExists) {
                throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Unfinished game round exists. Please continue the game.");
            }
        }

        boolean lockPlayer = isLockingPlayerRequired(settings);
        if (lockPlayer) {
            acquireLockOnPlayerAndGame(gameSession.getPlayer(), gameSkin.getUid());
        }
    }


    public JsonNode ack(GameSession gameSession, String gameRoundUid, JsonNode requestJsonNode) {

        GameRound gameRound = gameRoundService.findOne(gameRoundUid);
        if (gameRound == null)
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Request failed!");

        GameSkin gameSkin = gameSkinService.findOneByUid(gameRound.getGameId());

        if (!requestJsonNode.has("gameActivity")) {
            throw new BaseRuntimeException(SystemErrorCode.INACTIVE_GAME);
        }
        String gameActivityUid = requestJsonNode.get("gameActivity").asText();
        JsonNode gamePlayJsonNode = gamePlayService.findGamePlay(gameRound.getGamePlay());
        JsonNode gameActivityJsonNode = gamePlayService.findGameActivity(gameActivityUid);
        ObjectNode ackResponse = gamePlayService.ackGamePlay(gameSkin, requestJsonNode, gamePlayJsonNode, gameActivityJsonNode);
        gamePlayService.updateAckResponse(gameActivityUid, ackResponse);
        return ackResponse;
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

    protected DBObject findGamePlay(String gamePlayUid) {
        DBObject gamePlayDbObject = gamePlayStore.findOneDBObjectByUidAndStatus(gamePlayUid, Status.INPROGRESS);
        if (gamePlayDbObject == null)
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Game play not found or not in progress");

        return gamePlayDbObject;
    }

   /* protected Mono<GameMapDocument> saveGameMapForGamePlay(GameMapDocument gameMapDocument) {
        //Query query = query(where("gameUid").is(gameMapDocument.getGameUid()).and("playerUid").is(gameMapDocument.getPlayerUid()));
        return gameMapStore.updateByUid(gameMapDocument.getUid(),
                Map.of("gameUid", gameMapDocument.getGameUid(),
                        "playerUid", gameMapDocument.getPlayerUid(),
                        "gamePlayUid", gameMapDocument.getGamePlayUid(),
                        "modifiedOn", new Date()),
                true);
    }*/


    public JsonNode gameRoundDetails(GameRound gameRound) {


        Query playerQuery = Query.query(Criteria.where("uid").is(gameRound.getPlayer()));
        playerQuery.fields().include("correlationId");

        GamePlayDocument dbObject = gamePlayStore.findOneByUid(gameRound.getGamePlay());
        JsonNode gamePlayJsonNode = null;
        if (dbObject != null)
            getObjectMapper().enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
                    .valueToTree(dbObject);
        else
            gamePlayJsonNode = getObjectMapper().createObjectNode();

        GameSession gameSession = gameSessionService.findOneByUid(gameRound.getSession());

        Player player = playerService.findOneByUid(gameRound.getPlayer());

        ((ObjectNode) gamePlayJsonNode).remove("_id");

        ObjectNode gameRoundJsonNode = null;
        //log.info("game round  {} to object node", gameRound.getUid());
        try {
            String as = getObjectMapper().writer().writeValueAsString(gameRound);
            //log.info("game round json {}", as);
            gameRoundJsonNode = (ObjectNode) getObjectMapper().reader().readTree(as);
        } catch (JsonProcessingException e) {
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e.getMessage());
        }

        gameRoundJsonNode.remove("id");
        gameRoundJsonNode.remove("transactions");
        //gameRoundJsonNode.set("gamePlay", gamePlayJsonNode);
        gameRoundJsonNode.put("session", (String) gameSession.getToken());

        gameRoundJsonNode.put("player", gameRound.isDemo()
                ? gameRound.getPlayer()
                : (String) player.getCorrelationId());
        gameRoundJsonNode.set("gamePlay", gamePlayJsonNode);
        return gameRoundJsonNode;
    }

    public JsonNode gamePlayAndActivityDetails(GameRound gameRound) {
        JsonNode gameRoundDetailsJsonNode = gameRoundDetails(gameRound);


        if (gameRound.getGamePlay() != null) {
            JsonNode gamePlay = gameRoundDetailsJsonNode.get("gamePlay");
            List<DBObject> gameActivityList = gameActivityStore.findAllByGamePlay(gameRound.getGamePlay());

            ArrayNode gameActivityArrayNode = gameActivityList.stream().map(activityDbObject -> {

                        JsonNode gameActivityJsonNode;
                        try {
                            gameActivityJsonNode = objectMapper.readTree(objectMapper.writeValueAsString(activityDbObject));
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }


                        JsonNode streakCounterJsonNode = gameActivityJsonNode.get("streakCounter");
                        log.info("streakCounterJsonNode {}", streakCounterJsonNode);
                        return gameActivityJsonNode;
                    })
                    .collect(() -> objectMapper.createArrayNode(), ArrayNode::add, ArrayNode::addAll);


            ((ObjectNode) gameRoundDetailsJsonNode).set("gameActivities", gameActivityArrayNode);

            return gameRoundDetailsJsonNode;
        } else {
            return gameRoundDetailsJsonNode;
        }
    }

    public JsonNode filterJsonResponse(JsonNode response, String nodeType) {
        if (response.has("_id")) {
            ((ObjectNode) response).remove("_id");
        }
        return response;
    }

    public Boolean acquireLockOnPlayerAndGame(String player, String game) {

        Boolean acquired = getRedisTemplate().opsForValue()
                .setIfAbsent(player + "-" + game, true, Duration.ofSeconds(10));

        if (Boolean.FALSE.equals(acquired))
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Another game request is being processed. Please try again later.");
        return acquired;
    }

    public Boolean releaseLockOnPlayerAndGame(String player, String game) {

        return getRedisTemplate().delete(player + "-" + game);

    }


    public JsonNode composeGameResponse(GamePlayResponse gamePlayResponse) {
        ObjectNode response = objectMapper.createObjectNode();
        GameRound gameRound = gamePlayResponse.getGameRound();
        if (gameRound != null) {
            response.put("uid", gameRound.getUid());
            response.put("status", gameRound.getStatus().name());
            response.set("totalWager", objectMapper.createObjectNode().put("amount",
                            gameRound.getTotalWager().getNumber().doubleValue())
                    .put("currency", gameRound.getTotalWager().getCurrency().getCurrencyCode()));

            response.set("totalWin", objectMapper.createObjectNode().put("amount",
                            gamePlayResponse.getTotalWinnings())
                    .put("currency", gameRound.getTotalWager().getCurrency().getCurrencyCode()));

            /*if(gameRound.getJackpotDetails()!=null && gameRound.getJackpotDetails().getTotalJackpotWinningsInPlayerCurrency().isPositive()){
                response.set("jackpotDetails", getObjectMapper().valueToTree(gameRound.getJackpotDetails()));
            }*/

            if (gameRound.getPromoBonus() != null) {
                response.set("promoBonus", objectMapper.valueToTree(gameRound.getPromoBonus()));
            }
        }
        if (gamePlayResponse.getPlayerWallet() != null)
            response.set("wallet", objectMapper.valueToTree(gamePlayResponse.getPlayerWallet()));

        if (gamePlayResponse.getEngineResponse().has("gameClientResponse")) {
            // response.set(GAME_PLAY, engineResponse.get(GAME_CLIENT_RESPONSE).get(GAME_PLAY));
            //response.set(GAME_ACTIVITY, engineResponse.get(GAME_CLIENT_RESPONSE).get(GAME_ACTIVITY));
            response.set(GAME_CLIENT_RESPONSE, gamePlayResponse.getEngineResponse().get(GAME_CLIENT_RESPONSE));
        } else {
            response.set("gamePlay", gamePlayResponse.getGamePlay());
            response.set("gameActivity", gamePlayResponse.getGameActivity());
        }
/*
        if (gamePlayResponse.getStreakDisplayInfo() != null && !gamePlayResponse.getStreakDisplayInfo().isEmpty()) {
            response.set("promotionData", objectMapper.createObjectNode().set("streakInfo", gamePlayResponse.getStreakDisplayInfo()));
        }*/
        if (gamePlayResponse.getPromoBonus() != null) {
            ObjectNode promoBonus = objectMapper.createObjectNode().set("promoBonus",
                    objectMapper.valueToTree(gamePlayResponse.getPromoBonus()));
            if (response.has("promotionData"))
                ((ObjectNode) response.get("promotionData")).setAll(promoBonus);
            else
                response.set("promotionData", promoBonus);
        }


        return response;
    }


    public SearchResponse<JsonNode> history(SearchRequest searchRequest) {

        SearchResponse<GameRound> gameRoundSearchResponse = gameRoundService.search(searchRequest);

        List<JsonNode> gamePlayHistory = gameRoundSearchResponse.getItems()
                .stream().map(gameRound -> gamePlayAndActivityDetails(gameRound))
                .toList();

        SearchResponse<JsonNode> response = new SearchResponse<>();
        response.setCount(gameRoundSearchResponse.getCount());
        response.setItems(gamePlayHistory);
        return response;

    }

    public URI gameReplay(GameReplayRequest gameReplayRequest) {
        GameRound gameRound = gameRoundService.findOne(gameReplayRequest.getGameRound());
        if (gameRound == null)
            return null;

        if (!gameRound.getStatus().equals(Status.COMPLETED)) {
            return null;
        }

        GameSession gameSession = gameSessionService.findOneByUid(gameRound.getSession());
        GameSkin gameSkin = gameSkinService.findOneByUid(gameRound.getGameId());
        Brand brand = brandService.findOneByUid(gameRound.getBrand());

        return gameSupplierLocator.getSupplier(gameSkin.getConnector())
                .replayGameRound(gameSession, gameRound, gameSkin, brand);
    }

    public JsonNode replayGameRoundInitialiseGame(String gameRoundId) {

        GameRound gameRound = gameRoundService.findOne(gameRoundId);
        if (gameRound == null || Status.COMPLETED.equals(gameRound.getStatus()))
            throw new BaseRuntimeException(SystemErrorCode.INVALID_GAME_ROUND, "Invalid game round for replay");


        GameSession gameSession = gameSessionService.findOneByUid(gameRound.getSession());

        ObjectNode config = engineAdaptor
                .getGameClientConfig(gameSession.getGameConfiguration());

        ObjectNode response = objectMapper.createObjectNode();
        config = config.set("minMax", objectMapper.createArrayNode().add(gameRound.getTotalWager().getNumber().doubleValueExact()).add(1000));
        config = config.put("defaultStake", gameRound.getTotalWager().getNumber().doubleValueExact());
        config = config.set("ladder", objectMapper.createArrayNode().add(gameRound.getTotalWager().getNumber().doubleValueExact()));

        ObjectNode finalConfig = config;
        JsonNode gameRoundWithGamePlayAndGameActivityJsonNode = gamePlayAndActivityDetails(gameRound);


        response.set("config", finalConfig);
        response.set("gameRoundDetails", gameRoundWithGamePlayAndGameActivityJsonNode);
        return response;

    }
}
