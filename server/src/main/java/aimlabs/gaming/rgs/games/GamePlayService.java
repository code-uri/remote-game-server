package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gameactivities.GameActivityStore;
import aimlabs.gaming.rgs.gameplay.GamePlayDocument;
import aimlabs.gaming.rgs.gameplay.GamePlayStore;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.BasicDBObject;

import com.mongodb.DBObject;
import in.aimlabs.gaming.engine.api.exception.GameEngineException;
import in.aimlabs.gaming.engine.api.model.StakeSettings;


import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static aimlabs.gaming.rgs.core.utils.ObjectMapperUtils.convertToJsonNode;


//import static in.aimlabs.gaming.gaming.api.utils.ObjectMapperUtils.convertToJsonNode;

//import static in.aimlabs.gaming.gaming.utils.ObjectMapperUtils.convertToJsonNode;

@Data
@Slf4j
@Service
public class GamePlayService {
    //json fields

    public static final String GAME_PLAYS_COLLECTION = "GamePlays";
    public static final String GAME_ACTIVITIES_COLLECTION = "GameActivities";

    public static final String UID = "uid";
    public static final String GAME_PLAY = "gamePlay";
    public static final String GAME_ROUND = "gameRound";
    public static final String GAME_PLAY_STATE = "gamePlayState";
    public static final String GAME_STATUS = "gameStatus";
    public static final String WALLET = "wallet";
    public static final String GAME_ACTIVITIES = "gameActivities";
    public static final String GAME_ACTIVITY = "gameActivity";
    //json fields
    //public static final String SPIN_PAYOUT = "spinPayout";
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GamePlayStore gamePlayStore;

    @Autowired
    private GameActivityStore gameActivityStore;

    @Autowired
    private GameEngineServiceAdaptor gameEngineServiceAdaptor;

    @Value("${rgs.gaming.player-bag.name:playerBag}")
    private String PLAYER_BAG;

    @PostConstruct
    void init() {
        this.objectMapper = objectMapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    public GamePlayResponse proceedGamePlay(GamePlayContext gamePlayContext,
                                                  JsonNode gamePlayJsonNode) {

        Map<String, Object> settings = gamePlayContext.getSettings();
        StakeSettings stakeSettings =
                getObjectMapper().convertValue(settings, StakeSettings.class);

        GameSkin gameSkin = gamePlayContext.getGameSkin();
        GameSession gameSession = gamePlayContext.getGameSession();
        return new GamePlayResponse(gameEngineServiceAdaptor
                .play(gameSkin.getUid(),
                        gameSession.getUid(),
                        gameSession.getGameConfiguration(),
                        (ObjectNode) gamePlayContext.getGamePlayRequest(), (ObjectNode) gamePlayJsonNode, null, stakeSettings));
    }



    public ObjectNode ackGamePlay(GameSkin gameSkin, JsonNode requestJsonNode, JsonNode gamePlay, JsonNode gameActivity) {
        return gameEngineServiceAdaptor
                .ack( gamePlay.get("gameConfiguration").asText(),
                        (ObjectNode)requestJsonNode,
                        (ObjectNode)gamePlay,
                        (ObjectNode)gameActivity);
    }

    public JsonNode saveGamePlayAndPushGameActivity(GamePlayResponse gamePlayResponse,
                                                          GameSession gameSession) {
        //log.info("gameplay response {}", gamePlayResponse.getEngineResponse());

        JsonNode gamePlayNode = gamePlayResponse.getGamePlay();
        String uid = gamePlayNode.get(UID).asText();
        ObjectNode gameActivity = (ObjectNode)  gamePlayResponse.getGameActivity();
        if (gameActivity != null) {
            gameActivity.put("uid", gamePlayResponse.getGameActivityUid());
            Double spinPayoutNode = gamePlayResponse.getActivityWinnings();
            BasicDBObject gameActivityDBObject;

            try {
                gameActivityDBObject = BasicDBObject.parse(objectMapper
                        .writeValueAsString(gameActivity));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                throw new GameEngineException("Failed to save gameActivity and gamePlay", e);
            }


            gameActivityDBObject.put(GAME_PLAY, uid);
            gameActivity.put("tenant", gameSession.getTenant());
            gameActivityDBObject.put("demo", gameSession.isDemo());
            gameActivityDBObject.put(GAME_ROUND, gamePlayResponse.getGameRoundId());
            gameActivityDBObject.put("player", gameSession.getPlayer());

            gameActivityDBObject.put("status", Status.COMPLETED.name());
            gameActivityDBObject.put("createdOn", new Date());
            gameActivityDBObject.put("modifiedOn", new Date());

            if (gamePlayNode.get("gameType") != null)
                gameActivityDBObject.put("gameType", gamePlayNode.get("gameType").asText());

            if (gamePlayNode.get("gameId") != null)
                gameActivityDBObject.put("gameId", gamePlayNode.get("gameId").asText());

            /*if (spinPayoutNode > 0)
                gameActivityDBObject.put(SPIN_PAYOUT, spinPayoutNode);*/



            //log.info("player transaction auto played {}", autoPlayed);
            gameActivityDBObject.put("autoPlayed", false);
            DBObject  savedGameActivity = gameActivityStore.insertDBObject(gameActivityDBObject);
            updateGamePlay(gamePlayResponse, gameSession);
        }
        return updateGamePlay(gamePlayResponse, gameSession);
    }

    public JsonNode findGamePlay(String gamePlayUid) {
        GamePlayDocument gamePlayDocument = this.gamePlayStore.findOneByUid(gamePlayUid);
        if(gamePlayDocument!=null)
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Gameplay not found");

        return convertToJsonNode(objectMapper, gamePlayDocument);
    }

    public JsonNode findGamePlay(String gamePlayUid, Status status) {
        DBObject gamePlayDBObject = this.gamePlayStore.findOneDBObjectByUidAndStatus(gamePlayUid, status);

        if(gamePlayDBObject==null)
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Gameplay not found");
        return convertToJsonNode(objectMapper, gamePlayDBObject);
    }

    public JsonNode findGameActivity(String gameActivityUid) {
        DBObject gameActivityDBObject = this.gameActivityStore.findOneDBObjectByUid(gameActivityUid);
        if(gameActivityDBObject==null)
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Gameplay not found");
        return convertToJsonNode(objectMapper, gameActivityDBObject);
    }

    protected JsonNode updateGamePlay(GamePlayResponse gamePlayResponse, GameSession gameSession) {
        JsonNode gamePlayNode = gamePlayResponse.getGamePlay();
        String uid = gamePlayNode.get(UID).asText();

        String  gamePlayStatus = gamePlayResponse.getGamePlayStatus();
        ((ObjectNode)gamePlayNode).put("status", gamePlayStatus);
        /*if (gamePlayNode.get("gameId") != null)
            gameActivityDBObject.put("gameId", gamePlayNode.get("gameId").asText());
        */
        ((ObjectNode)gamePlayNode).put("player", gameSession.getPlayer());

        BasicDBObject gamePlayDBObject = BasicDBObject.parse(gamePlayNode.toPrettyString());





        try {
            Map<String, Object> gamePlayMap = objectMapper.convertValue(gamePlayNode,
                    new TypeReference<Map<String, Object>>(){});

            //if(!gamePlayDBObject.containsKey("createdOn")){
            gamePlayMap.put("createdOn", new Date());
            //}
            gamePlayMap.put("modifiedOn", new Date());
            DBObject gamePlay = gamePlayStore
                    .updateOrInsert(uid, gamePlayMap);


            JsonNode jsonNode = null;
            try {
                jsonNode = this.objectMapper.readTree(this.objectMapper.writeValueAsString(gamePlay));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR,e);
            }

            return jsonNode;
        } catch (Exception e) {
            throw new GameEngineException("Failed to save table game play", e);
        }
    }


    /*public Mono<DBObject> saveGamePlay(JsonNode gamePlayJsonNode) {
        String status = gamePlayJsonNode.get("gamePlayState").get("gameStatus").asText();
        ((ObjectNode) gamePlayJsonNode).put("status", status);
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(gamePlayJsonNode))
                .switchIfEmpty(Mono
                        .error(new GameEngineException("Failed to save gamePlay " + gamePlayJsonNode.get("uid"))))
                .flatMap(gamePlayJson -> {
                    BasicDBObject gamePlay = BasicDBObject.parse(gamePlayJson);
                    gamePlay.put("createdOn", new Date());
                    gamePlay.put("modifiedOn", new Date());
                    return gamePlayStore.insertDBObject(gamePlay);
                });
    }*/

    public ObjectNode prepareGameClientResponse(String gameType,String gameConfiguration, JsonNode gamePlay, JsonNode gameActivity) {

        return gameEngineServiceAdaptor
                .prepareGameClientResponse(gameConfiguration,
                        (ObjectNode)gamePlay,
                        (ObjectNode)gameActivity);

    }

    public void updateAckResponse(String uid, JsonNode ackResponse) {

        try {
            String gamePlayJsonNode = objectMapper.writeValueAsString(ackResponse);
            BasicDBObject ackDBObject = BasicDBObject.parse(gamePlayJsonNode);
            gameActivityStore.updateAckResponse(uid, ackDBObject);

        } catch (JsonProcessingException e) {
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Failed to save ack response for activity" + uid, e);
        }
    }
}
