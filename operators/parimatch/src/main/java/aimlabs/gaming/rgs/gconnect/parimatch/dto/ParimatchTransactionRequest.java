package aimlabs.gaming.rgs.gconnect.parimatch.dto;

import aimlabs.gaming.rgs.gameoperators.Wallet;
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
