package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.players.PlayerWallet;
import aimlabs.gaming.rgs.promotions.FreeSpinCampaign;
import aimlabs.gaming.rgs.promotions.PromoBonus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.util.Optional;

@Data
public class GamePlayResponse {

    public static final String UID = "uid";
    public static final String TOTAL_BET = "totalWager";
    public static final String GAME_PLAY = "gamePlay";
    public static final String GAME_PLAY_STATE = "gamePlayState";
    public static final String GAME_STATUS = "gameStatus";
    public static final String TOTAL_WINNINGS = "totalWin";
    public static final String WALLET = "wallet";
    public static final String GAME_ACTIVITIES = "gameActivities";
    public static final String GAME_ACTIVITY = "gameActivity";
    //public static final String SPIN_PAYOUT = "spinPayout";
    public static final String GAME_CLIENT_RESPONSE = "gameClientResponse";
    public static final String GAME_ACTIVITY_DEBIT_AMOUNT = "wager";
    //public static final String WINNINGS = "winnings";
    public static final String ACTIVITY_WINNINGS = "win";
    public static final String PLAYER_BAG = "playerBag";

    ObjectNode engineResponse;
    PlayerWallet playerWallet;
    PromoBonus promoBonus;
    FreeSpinCampaign freeSpins;
    String freeSpinsAllotmentId;
    String gameRoundId;
    GameRound gameRound;
    boolean continueRound;
    String gameActivityUid;
    double additionalWager;
    JsonNode gamePlayRequest;

    public GamePlayResponse(ObjectNode engineResponse) {
        this.engineResponse = engineResponse;
    }

    public JsonNode getGamePlay() {
        if (!engineResponse.has(GAME_PLAY) && engineResponse.has(GAME_CLIENT_RESPONSE))
            return engineResponse.get(GAME_CLIENT_RESPONSE).get(GAME_PLAY);

        return engineResponse.get(GAME_PLAY);
    }

    public JsonNode getGameActivity() {
        if (!engineResponse.has(GAME_ACTIVITY) && engineResponse.has(GAME_CLIENT_RESPONSE))
            return engineResponse.get(GAME_CLIENT_RESPONSE).get(GAME_ACTIVITY);
        return engineResponse.get(GAME_ACTIVITY);
    }

    public JsonNode getGamePlayState() {
        return getGamePlay().get(GAME_PLAY_STATE);
    }

    public JsonNode getPlayerBag() {
        JsonNode extraData =  getGamePlay().get("data");
        if (extraData != null && extraData.has(PLAYER_BAG))
            return extraData.get(PLAYER_BAG);
        return null;
    }

    public String getGamePlayStatus() {
        JsonNode gps = getGamePlayState();
        String gamePlayStatus = gps.get(GAME_STATUS).asText();
        ((ObjectNode) getGamePlay()).put("status", gamePlayStatus);
        return gamePlayStatus;
    }

    public void addActivityWinnings(double wins) {
        JsonNode gameActivityJsonNode = engineResponse.get(GAME_ACTIVITY);
        double totalWins =0;
        if (gameActivityJsonNode.has(ACTIVITY_WINNINGS))
            totalWins =  gameActivityJsonNode.get(ACTIVITY_WINNINGS).asDouble(0);
        else
            totalWins =  0;

        ((ObjectNode)gameActivityJsonNode).put(ACTIVITY_WINNINGS, totalWins + wins);
    }

    public double getActivityWinnings() {
        JsonNode gameActivityJsonNode = getGameActivity();
        if (gameActivityJsonNode.has(ACTIVITY_WINNINGS))
            return gameActivityJsonNode.get(ACTIVITY_WINNINGS).asDouble(0);
        else
            return 0;
    }


    public double getWinnings() {
        if (engineResponse.has(ACTIVITY_WINNINGS))
            return engineResponse.get(ACTIVITY_WINNINGS).asDouble(0);

        else
            return 0;
    }

    public boolean isRoundCompleted() {
        if(engineResponse.has("roundCompleted"))
            return engineResponse.get("roundCompleted").asBoolean(false);
        else
             return "COMPLETED".equals(getGamePlayStatus());
    }

    public void setTotalWager(double wager) {
        engineResponse.put("totalWager", wager);
    }

    public double getTotalWager() {
        return engineResponse.get("totalWager").asDouble();
    }

    public void addTotalWager(double wager) {
        double totalWager = engineResponse.get("totalWager").asDouble();

        engineResponse.put("totalWager", totalWager + wager);
    }

    public void addTotalWinnings(double v) {
        engineResponse.put("totalWin", v);
    }

    public double getTotalWinnings() {
        if (engineResponse.has(TOTAL_WINNINGS))
            return engineResponse.get(TOTAL_WINNINGS).asDouble(0);
        else
            return 0;
    }

    public JsonNode getEngineResponse() {
        return engineResponse;
    }

    public PromoBonus getPromoBonus() {
        return promoBonus;
    }

    public void setPromoBonus(PromoBonus promoBonus) {
        this.promoBonus = promoBonus;
    }

    public String getGameRoundId() {
        return gameRoundId;
    }

    public void setGameRoundId(String gameRoundId) {
        this.gameRoundId = gameRoundId;
    }

    public GameRound getGameRound() {
        return gameRound;
    }

    public void setGameRound(GameRound gameRound) {
        this.gameRound = gameRound;
    }

    public void setGameActivityUid(String gameActivityUid) {
        this.gameActivityUid = gameActivityUid;
    }

    public String getGameActivityUid() {
        return gameActivityUid;
    }

    public PlayerWallet getPlayerWallet() {
        return playerWallet;
    }

    public void setPlayerWallet(PlayerWallet playerWallet) {
        this.playerWallet = playerWallet;
    }

    public boolean isContinueRound() {
        return continueRound;
    }

    public boolean isNewRound() {
        return !continueRound;
    }

    public void setContinueRound(boolean continueRound) {
        this.continueRound = continueRound;
    }

    public double getAdditionalWager() {
        JsonNode gameActivity = getGameActivity();
        if (gameActivity.has(GAME_ACTIVITY_DEBIT_AMOUNT) && gameActivity.get(GAME_ACTIVITY_DEBIT_AMOUNT).doubleValue() > 0)
            return gameActivity.get(GAME_ACTIVITY_DEBIT_AMOUNT).doubleValue();

        return additionalWager;
    }

    public void setAdditionalWager(Double additionalWager) {
        this.additionalWager = additionalWager;
    }

    public JsonNode getGamePlayRequest() {
        return gamePlayRequest;
    }

    public void setGamePlayRequest(JsonNode gamePlayRequest) {
        this.gamePlayRequest = gamePlayRequest;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("GamePlayResponse{");
        sb.append("engineResponse=").append(engineResponse);
        sb.append('}');
        return sb.toString();
    }

    public Double getStreakWager() {
        return gamePlayRequest.has("streakWager") ? gamePlayRequest.get("streakWager").asDouble(): null;
    }

    public JsonNode getStreakUpdate() {

        JsonNode gameActivityJsonNode = getGameActivity();
        return gameActivityJsonNode.get("streakUpdate");
    }

    public JsonNode setStreakCounter(JsonNode streakCounter) {
        ObjectNode gameActivityJsonNode = (ObjectNode) getGameActivity();
        return gameActivityJsonNode.set("streakCounter", streakCounter);
    }

    public JsonNode getStreakCounter() {
        ObjectNode gameActivityJsonNode = (ObjectNode) getGameActivity();
        return gameActivityJsonNode.get("streakCounter");
    }
}