package in.aimlabs.gaming.gconnect.astroplay.dto;

import lombok.Data;

@Data
public class GameUrlRequest {
    private long gameId;
    private long clientId;
    private long operatorId;
    private String playerId;
    private String currency = "";
    private String language = "";
    private String playerCountryIsoCode = "";
    private String playerName = "";
    private String additionalInfo = "";
    private Long playerIdInteger;
}