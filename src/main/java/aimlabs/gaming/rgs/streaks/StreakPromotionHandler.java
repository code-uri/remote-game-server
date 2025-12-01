package aimlabs.gaming.rgs.streaks;

import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.games.*;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.players.Player;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.result.UpdateResult;
import in.aimlabs.gaming.engine.api.dto.StreakUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class StreakPromotionHandler implements GameHandler, GameInitializer {

    GameHandler nextHandler;

    @Autowired
    StreakCounterStore streakCounterStore;

    @Autowired
    StreakCounterMapper streakCounterMapper;

    @Autowired
    GameEngineServiceAdaptor engineAdaptor;


    @Autowired
    ObjectMapper objectMapper;

    private static int getStreakFromCounter(StreakCounter streakCounter, StreakUpdate streakUpdate) {
        return streakCounter.getStatus() == Status.COMPLETED
                ? 0
                : (streakUpdate.getOperation() == StreakUpdate.Operation.INCREMENT
                ? streakCounter.getStreak() + 1
                : streakCounter.getStreak());
    }

    @Override
    public void handle(JsonNode request, GamePlayContext context) {

        ObjectNode config = engineAdaptor.getGameClientConfig(context.getGameSession().getGameConfiguration());

        if (config.has("streakSupported")) {
            GamePlayResponse gamePlayResponse = context.getEngineResponse();
            GameSession gameSession = context.getGameSession();
            GameSkin gameSkin = context.getGameSkin();
            Player player = context.getPlayer();

            if (gamePlayResponse.getStreakUpdate() != null) {
                JsonNode gamePlayState = gamePlayResponse.getGamePlayState();

                if (gamePlayState.has("streakSupported")) {
                    if (!gamePlayState.get("streakSupported").asBoolean(false)) {
                        return;
                    }
                }

                Map<String, Object> settings = context.getSettings();
                if (settings.get("streakWager") == null || !(settings.get("streakWager") instanceof Double)) {
                    throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "streak not supported for this currency");
                }

                if (!gamePlayState.has("streakBonusMultipliers")) {
                    throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "streak multipliers are not defined");
                }

                ArrayNode streakBonusMultipliers = (ArrayNode) gamePlayState.get("streakBonusMultipliers");

                List<Double> streakBonusMultipliersList = Arrays.stream(objectMapper.convertValue(streakBonusMultipliers, Double[].class)).toList();

                JsonNode streakUpdateJsonNode = gamePlayResponse.getStreakUpdate();
                StreakUpdate streakUpdate = objectMapper.convertValue(streakUpdateJsonNode, StreakUpdate.class);

                StreakCounter streakCounter;
                StreakUpdate.Operation operation = streakUpdate.getOperation();

                if (operation == StreakUpdate.Operation.RESET) {
                    streakCounter = streakCounterStore.endStreak(player.getUid(),
                            gameSession.getGame(),
                            gamePlayResponse.getGameRoundId(),
                            streakUpdate.getLastGameResult(),
                            streakBonusMultipliersList);
                } else {
                    // This handles INCREMENT, SET, and any other operation by calling incrementStreakForPlayer.
                    // The increment amount is 1 for INCREMENT, and 0 for SET or any other operation.
                    int incrementAmount = (operation == StreakUpdate.Operation.INCREMENT) ? 1 : 0;
                    streakCounter = streakCounterStore.incrementStreakForPlayer(gameSession.getPlayer(),
                            gameSession.getGame(),
                            gamePlayResponse.getGameRoundId(),
                            streakUpdate.getLastGameResult(),
                            (Double) settings.get("streakWager"),
                            gameSession.getCurrency(),
                            streakBonusMultipliersList,
                            incrementAmount);

                    //final streak reached. end streak and award streak wins.
                    if (operation == StreakUpdate.Operation.INCREMENT
                        && streakCounter.getStreak() + 1 == streakBonusMultipliersList.size()) {
                        streakCounter = streakCounterStore.endStreak(player.getUid(),
                                gameSession.getGame(),
                                gamePlayResponse.getGameRoundId(),
                                streakUpdate.getLastGameResult(),
                                streakBonusMultipliersList);
                    }
                }

                if(streakCounter!=null) {

                    gamePlayResponse = addStreakInfoToResponse(context,
                            getStreakFromCounter(streakCounter, streakUpdate),
                            streakCounter.getStreak(),
                            null,
                            streakCounter);
                }else{
                    Double streakWager = (Double) settings.get("streakWager");

                    if (streakWager > 0) {
                        gamePlayResponse = addStreakInfoToResponse(context,
                                streakUpdate.getOperation() == StreakUpdate.Operation.INCREMENT ? 1 : 0,//streak count
                                0,
                                BigDecimal.valueOf(streakWager),
                                null);


                        ObjectNode gameClientResponseNode = (ObjectNode) gamePlayResponse.getEngineResponse();
                        gameClientResponseNode.put("streakWager", streakWager);
                        gamePlayResponse.addTotalWager(streakWager);

                    }
                }
            }
        }

        nextHandler.handle(request, context);
    }

    @Override
    public void setNext(GameHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    private GamePlayResponse addStreakInfoToResponse(GamePlayContext context,
                                                     int streak,
                                                     int previousStreak,
                                                     BigDecimal streakWager,
                                                     StreakCounter streakCounter) {

        ObjectNode gamePlay = (ObjectNode) context.getEngineResponse().getGamePlay();
        ObjectNode gameActivity = (ObjectNode) context.getEngineResponse().getGameActivity();

        gameActivity.put("streakWager", streakWager);
        gameActivity.put("streak", streak);

        BigDecimal streakWin = streakCounter != null ? streakCounter.getStreakWin() : null;

        if (streakWin != null) {
            gameActivity.put("streakWin", streakWin);
            context.getEngineResponse().addActivityWinnings(streakWin.doubleValue());
            ((ObjectNode) gamePlay.get("gamePlayState")).put("streakWin", streakWin);
        }


        ObjectNode gameClientResponse = context.getEngineResponse().getEngineResponse().has("gameClientResponse") ?
                (ObjectNode) context.getEngineResponse().getEngineResponse().get("gameClientResponse") :
                (ObjectNode) context.getEngineResponse().getEngineResponse();
        gameClientResponse.put("streakWager", streakWager);
        gameClientResponse.put("streak", streak);
        gameClientResponse.put("streakWin", streakWin);


        Update gameActivityUpdate = Update.update("streak", streak);
        Update gamePlayUpdate = Update.update("gamePlayState.startStreakCount", previousStreak);

        if (streakWager != null)
            gamePlayUpdate = gamePlayUpdate.set("gamePlayState.streakWager", streakWager);

        if (streakWin != null) {
            gameActivityUpdate = gameActivityUpdate.set("streakWin", streakWin).inc("win", streakWin);
            gamePlayUpdate = gameActivityUpdate.set("gamePlayState.streakWin", streakWin);
        }

        UpdateResult gamplayresult = streakCounterStore.getTemplate().updateFirst(Query.query(Criteria.where("uid")
                .is((gamePlay.get("uid").asText()))), gamePlayUpdate, "GamePlays");

        UpdateResult gameActivityResult = streakCounterStore.getTemplate().updateFirst(Query.query(Criteria.where("uid")
                        .is(context.getEngineResponse().getGameActivityUid())),
                gameActivityUpdate, "GameActivities");

        return context.getEngineResponse();
    }

    public JsonNode loadData(GameSession gameSession, GameSkin gameSkin, Map<String, Object> settings) {
        StreakCounterDocument streakCounterDocument = streakCounterStore.findActiveStreak(gameSession.getGame(), gameSession.getPlayer(), gameSession.getCurrency());


        ObjectNode playerBag = objectMapper.createObjectNode();
        if(streakCounterDocument!=null)
            playerBag.put("streak", streakCounterDocument.getStreak());
        return objectMapper.createObjectNode().set("playerBag", playerBag);
    }
}
