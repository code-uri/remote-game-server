package aimlabs.gaming.rgs.gameoperators;

import aimlabs.gaming.rgs.gamerounds.GameRoundStatusEnum;
import aimlabs.gaming.rgs.promotions.FreeSpinCampaign;
import aimlabs.gaming.rgs.promotions.JackpotWinnings;
import aimlabs.gaming.rgs.transactions.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * PlayerTransactionRequest
 */
@Data
public class PlayerTransactionRequest {

    @JsonIgnore
    private String brand;

    @JsonIgnore
    private String tenant;

    private String gameId;

    private String gameVersion;

    private String playerId;

    @JsonProperty("sessionToken")
    private String token;

    @JsonIgnore
    private String internalToken;

    private String currency;

    private String orgTxnUid;

    private Double orgTxnAmount;

    private Double debit;

    private Double credit;

    private TransactionType requestType;

    private FreeSpinCampaign freeSpins;

    private String gameRoundId;

    private String txnId;

    private List<JackpotWinnings> jackpotWinnings;

    private GameRoundStatusEnum gameRoundStatus;

    public String getGameId() {
        return gameId != null ? gameId.toLowerCase() : null;
    }

    public String getBrand() {
        return brand != null ? brand.toLowerCase() : null;
    }
}
