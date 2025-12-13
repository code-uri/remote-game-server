package aimlabs.gaming.rgs.gameoperators;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * PlayerTransactionResponse
 */
@Data
public class PlayerTransactionResponse {

    String txnId;
    @JsonProperty("playerWallet")
    private Wallet wallet;
    private Map<String, Object> processedTxnIds = new HashMap<>();
    private String rollbackTxnId;

    public PlayerTransactionResponse playerWallet(Wallet wallet) {
        this.wallet = wallet;
        return this;
    }
}

