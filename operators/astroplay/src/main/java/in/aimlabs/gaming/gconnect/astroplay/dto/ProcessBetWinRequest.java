package in.aimlabs.gaming.gconnect.astroplay.dto;

import lombok.Data;

@Data
public class ProcessBetWinRequest {
    private String integrationId;
    private String gameProviderGameId;
    private String playerId;
    private String currency;
    private long betAmountInCents;
    private String gameBetTransactionId;
    private long winAmountInCents;
    private String gameWinTransactionId;
    private String roundId;
    private String bonusId; // Nullable fields can be represented as String
    private String details; // Nullable fields can be represented as String
    private String gameSessionId; // Nullable fields can be represented as UUID
    private String extGameSessionId; // Nullable fields can be represented as String
    private Boolean isRoundFinished; // Nullable fields can be represented as Boolean

    // Additional methods or constructors can be added as needed
}