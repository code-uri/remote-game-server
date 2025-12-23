package aimlabs.gaming.rgs.gameoperators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PlayerBalanceRequest
 */
public class PlayerBalanceRequest {
    @JsonProperty("sessionToken")
    private String token;

    private String player;

    private String currency;

    @JsonIgnore
    private String internalToken;

    @JsonIgnore
    private String brand;

    @JsonIgnore
    private String gameId;

    @JsonIgnore
    String tenant;

    public PlayerBalanceRequest sessionToken(String token) {
        this.token = token;
        return this;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class PlayerBalanceRequest {\n");

        sb.append("    sessionToken: ").append(toIndentedString(token)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}
