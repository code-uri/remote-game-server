package in.aimlabs.gaming.gconnect.parimatch.dto;

import in.aimlabs.gaming.dto.Wallet;
import lombok.Data;

@Data
public class ParimatchTransactionRequest {

    String cid;
    String sessionToken;
    String playerId;
    String productId;
    long amount;
    String currency;
    String txId;
    String roundId;
    boolean roundClosed;
    Wallet wallet;

}
