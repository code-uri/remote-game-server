package in.aimlabs.gaming.gconnect.astroplay.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class ProcessRevokeRequest {
    private String integrationId;
    private String gameProviderGameId;
    private String playerId;
    private String currency;
    private String gameBetTransactionId;
    private String roundId;
    private String bonusId; // Nullable fields can be represented as String or Optional<String>
    private String details;
    private String gameSessionId; // Nullable fields can be represented as UUID or Optional<UUID>
    private String extGameSessionId;
    private Long amountInCents; // Nullable fields can be represented as Long or Optional<Long>
    private Boolean isRoundFinished; // Nullable fields can be represented as Boolean or Optional<Boolean>

    // You can add more fields or methods as needed based on your requirements
}