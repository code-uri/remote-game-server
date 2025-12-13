package in.aimlabs.gaming.gconnect.astroplay.dto;

import lombok.Data;

@Data
public class SessionState {
    private boolean isValid;
    private boolean isStarted;
    private String playerId;
    private String playerName;
    private String sessionId;
    private String gameSessionId;
    private String gameProviderGameId;
    private GameUrlRequest sessionData;
}
