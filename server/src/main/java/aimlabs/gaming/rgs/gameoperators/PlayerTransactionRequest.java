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


    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }

    public String getGameId() {
        return gameId!=null?gameId.toLowerCase():null;
    }

    public String getBrand() {
        return brand!=null?brand.toLowerCase():null;
    }
}

