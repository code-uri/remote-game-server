package in.aimlabs.gaming.gconnect.astroplay.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class BalanceResponse {
    private UUID transactionId;
    private double balance;
    private String currency;
    private String status;
    private String message;

    // You can add more fields as needed based on your requirements
}