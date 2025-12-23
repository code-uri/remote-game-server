package aimlabs.gaming.rgs.gameoperators;

import aimlabs.gaming.rgs.gamerounds.GameRoundStatusEnum;
import aimlabs.gaming.rgs.promotions.FreeSpinCampaign;
import aimlabs.gaming.rgs.promotions.JackpotWinnings;
import aimlabs.gaming.rgs.transactions.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * PlayerTransactionRequestV1
 */
@Data
@NoArgsConstructor
public class PlayerTransactionRequestV1 {

    @JsonIgnore
    private String brand;

    @JsonIgnore
    private String tenant;

    private String gameId;

    private String playerId;

    @JsonProperty("sessionToken")
    private String token;

    private String currency;

    private String orgTxnUid;

    private PlayerGameTransaction debit;

    private PlayerGameTransaction credit;

    private TransactionType requestType;

    private FreeSpinCampaign freeSpins;

    private String gameRoundId;

    private String txnId;

    private List<JackpotWinnings> jackpotWinnings;

    private GameRoundStatusEnum gameRoundStatus;

    public PlayerTransactionRequestV1(PlayerTransactionRequest request) {

        if (request.getDebit() != null)
            this.debit = new PlayerGameTransaction(BigDecimal.valueOf(request.getDebit()), request.getTxnId());
        if (request.getCredit() != null)
            this.credit = new PlayerGameTransaction(BigDecimal.valueOf(request.getCredit()), request.getTxnId());

        this.brand = request.getBrand();
        this.tenant = request.getTenant();
        this.gameId = request.getGameId();
        this.playerId = request.getPlayerId();
        this.token = request.getToken();
        this.currency = request.getCurrency();
        this.orgTxnUid = request.getOrgTxnUid();
        this.requestType = request.getRequestType();
        this.freeSpins = request.getFreeSpins();
        this.gameRoundId = request.getGameRoundId();
        this.txnId = request.getTxnId();
        this.jackpotWinnings = request.getJackpotWinnings();
        this.gameRoundStatus = request.getGameRoundStatus();
    }

    public String getGameId() {
        return gameId != null ? gameId.toLowerCase() : null;
    }

    public String getBrand() {
        return brand != null ? brand.toLowerCase() : null;
    }
}
