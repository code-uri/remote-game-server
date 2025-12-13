package in.aimlabs.gaming.gconnect.astroplay.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class IntegrationMinimalDto {
    private UUID providerId;
    private long operatorId;
    private String integrationName; // Example additional field
    private String status; // Example additional field
}