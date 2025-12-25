package aimlabs.gaming.rgs.gconnect.parimatch.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ParimatchTransactionResponse {
    String txId;
    String processedTxId;
    long balance;
    String createAt;
    boolean alreadyProcessed;
    String roundId;
    boolean roundClosed;

    @JsonCreator
    public ParimatchTransactionResponse(@JsonProperty("txId") String txId,
                                        @JsonProperty("processedTxId") String processedTxId,
                                        @JsonProperty("balance") long balance,
                                        @JsonProperty("createAt") String createAt) {
        this.txId = txId;
        this.processedTxId = processedTxId;
        this.balance = balance;
        this.createAt = createAt;
    }


    public ParimatchTransactionResponse() {

    }
}
