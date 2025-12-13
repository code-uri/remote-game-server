package in.aimlabs.gaming.gconnect.astroplay.dto;

import java.util.UUID;

public class IntegrationsBaseBalanceRequest {
    private String playerId;
    private String currency;
    private String gameId;
    private UUID sessionToken;
    private String extSessionToken;
    private String additionalInfo;
}