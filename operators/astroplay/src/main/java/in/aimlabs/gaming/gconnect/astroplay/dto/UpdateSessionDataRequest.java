package in.aimlabs.gaming.gconnect.astroplay.dto;

import lombok.Data;

@Data
public class UpdateSessionDataRequest {
    private String gameSessionId;
    private String extGameSessionId;
    private String newGameId;
    private String newExtGameSessionId;
}