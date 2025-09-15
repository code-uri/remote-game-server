package aimlabs.gaming.rgs.games;

import lombok.Data;

import java.io.Serializable;

@Data
public class GameSessionRequest implements Serializable {
    private String brand;
    private String playerId;
    private String currency;
    private String token;
    private String ipAddress;
    private String gameId;
}
