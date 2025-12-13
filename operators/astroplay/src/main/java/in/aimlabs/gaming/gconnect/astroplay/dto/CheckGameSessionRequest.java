package in.aimlabs.gaming.gconnect.astroplay.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CheckGameSessionRequest {

    private String sessionToken;
    private String extSessionToken;
    private String playerId;
    private Long playerIdNumeric;
    private String gameId;
}