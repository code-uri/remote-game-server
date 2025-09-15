package aimlabs.gaming.rgs.games;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class GameLaunchRequest implements Serializable {


    private String tenant = "default";
    private String locale;

    protected String token;
    protected boolean initSession;
    protected String externalToken;

    protected String gameId;
    protected boolean demo;
    protected String brand;
    protected String brandUrl;
    protected String clientId;
    protected String network;
    protected String language = "en";
    protected LaunchMode mode;
    protected GamePlayMode gamePlayMode;
    protected String limitsUrl;
    protected String gamePauseUrl;
    protected String selfTestUrl;
    protected String overlayUrl;
    protected String depositUrl;
    protected String lobbyUrl;
    protected String historyUrl;
    protected String ipAddress;
    protected String currency;
    protected String player;
    private String jurisdiction;
    private long realityCheckIntervalInMilliSeconds;
    private long elapsedTimeInMilliSeconds;
    private Double minBet;
    private Double maxTotalBet;
    private Double defaultBet;
    protected Integer audio;

    private Map<String, String> params = new HashMap<>();


    public GameLaunchRequest(String tenant, String network, String brand, String token, String gameId, Map<String, String> params) {
        this.tenant = tenant;
        this.brand = brand;
        this.network = network;
        this.gameId = gameId.toLowerCase().trim();

        this.language = params.get("language") != null ? params.remove("language").toLowerCase() : null;
        if (this.language == null)
            this.language = params.get("lang") != null ? params.remove("lang").toLowerCase() : null;


        this.locale = params.get("locale") != null ? params.remove("locale").toLowerCase() : null;
        //this.regulations = new HashSet<>(params.getOrDefault("regulation", new ArrayList<>()));

        this.brand = params.get("brand") != null ? params.remove("brand").toLowerCase().trim() : brand;
        if (this.brand == null)
            this.brand = params.get("cid") != null ? params.remove("cid").toLowerCase().trim() : null;

        //urls
        this.limitsUrl = params.get("limitsUrl") != null ? params.get("limitsUrl") : null;
        this.limitsUrl = params.get("limitsURL") != null ? params.get("limitsURL") : null;
        this.gamePauseUrl = params.get("gamePauseUrl") != null ? params.get("gamePauseUrl") : null;
        this.gamePauseUrl = params.get("gamePauseURL") != null ? params.get("gamePauseURL") : null;
        this.selfTestUrl = params.get("selfTestUrl") != null ? params.get("selfTestUrl") : null;
        this.selfTestUrl = params.get("selfTestURL") != null ? params.get("selfTestURL") : null;
        this.depositUrl = params.get("depositUrl") != null ? params.get("depositUrl") : null;
        this.depositUrl = params.get("depositURL") != null ? params.get("depositURL") : null;
        this.lobbyUrl = params.get("lobbyUrl") != null ? params.get("lobbyUrl") : null;
        this.lobbyUrl = params.get("lobbyURL") != null ? params.get("lobbyURL") : null;
        this.historyUrl = params.get("historyUrl") != null ? params.get("historyUrl") : null;
        this.historyUrl = params.get("historyURL") != null ? params.get("historyURL") : null;
        this.overlayUrl = params.get("overlayUrl") != null ? params.get("overlayUrl") : null;

        this.mode = params.get("launchMode") != null ? LaunchMode.valueOf(params.remove("launchMode")) : null;

        if(params.get("mode") != null)
            this.mode = LaunchMode.valueOf(params.remove("mode"));

        this.demo = token.toLowerCase().startsWith("demo");
        this.gamePlayMode = this.demo?GamePlayMode.DEMO:GamePlayMode.REAL;
        this.token = token;
        if (params.get("token") != null)
            params.remove("token");

        if(params.get("player")!=null)
            this.setPlayer(params.remove("player"));

        if(params.get("currency")!=null)
            this.setCurrency(params.remove("currency"));

        this.params = params;

    }




}
