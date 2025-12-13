package in.aimlabs.gaming.gconnect.parimatch.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.aimlabs.gaming.dto.Wallet;
import lombok.Data;

@Data
public class ParimatchPlayerInfoResponse {
    String playerId;
    long balance;
    String currency;
    String country;
    Wallet wallet;

    @JsonCreator
    public ParimatchPlayerInfoResponse(@JsonProperty("playerId") String playerId,
                                       @JsonProperty("balance") long balance,
                                       @JsonProperty("currency") String currency,
                                       @JsonProperty("country") String country) {
        this.playerId = playerId;
        this.balance = balance;
        this.currency = currency;
        this.country = country;


        wallet = getWallet();
    }

}
