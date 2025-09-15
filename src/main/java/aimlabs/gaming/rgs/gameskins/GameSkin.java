package aimlabs.gaming.rgs.gameskins;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;

@Data
public class GameSkin extends BaseDto {

    private String uid;
    private String brand;
    private String name;
    private String description;
    private String gameType;
    private Integer payLines;
    private String url;
    private String apiUrl;
    private String assetsBaseUrl;
    private String launchType = "REDIRECT";
    private String clientVersion;
    private String gameConfiguration;
    private String providerGame;
    private String connector = "local-connector";
    private String network = "default";
}
