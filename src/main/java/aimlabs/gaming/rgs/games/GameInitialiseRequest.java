package aimlabs.gaming.rgs.games;

import lombok.Data;

@Data
public class GameInitialiseRequest {

    private String brand;

    private String gameId;

    private String token;


    public String getBrand() {
        return brand!=null ?brand.toLowerCase():null;
    }

    public String getGameId() {
        return gameId!=null ?gameId.toLowerCase():null;
    }
}
