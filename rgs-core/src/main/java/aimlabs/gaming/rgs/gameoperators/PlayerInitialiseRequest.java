package aimlabs.gaming.rgs.gameoperators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * PlayerInitialiseRequest
 */
@Data
public class PlayerInitialiseRequest {
    @JsonIgnore
    private String brand;

    @JsonIgnore
    private String tenant;

    @JsonProperty("gameId")
    private String gameId;

    @JsonProperty("sessionToken")
    private String sessionToken;

    @JsonIgnore
    private String internalToken;

    private String player;

    @JsonIgnore
    private String currency;

}
