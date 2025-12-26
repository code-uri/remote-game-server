package aimlabs.gaming.rgs.gamesessions;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class GameSession extends BaseDto {

    private String uid;
    private String token;
    private String jwt;
    private boolean demo;
    private Date startedAt;
    private Date endedAt;
    private Date lastAccessedAt;
    private String ipAddress;
    private String player;
    private List<String> playerTags;
    private String brand;
    private String casinoUrl;
    private String network;
    private String currency;
    private String game;
    private String gameConfiguration;
    private String providerGame;
    private String pamConnector;
    private String gameConnector;
    private boolean aggregateCredits;
    private Map<String,String> urls = new HashMap<>();

    private String jurisdiction;
    private long realityCheckIntervalInSeconds;
    private long elapsedTimeInSeconds;

    private Double minBet;
    private Double maxTotalBet;
    private Double defaultBet;



    @JsonIgnore
    public boolean isDemoSession() {
        return token != null && token.toLowerCase().startsWith("demo");
    }

    public String getBrand() {
        return brand.toLowerCase();
    }

    public void setBrand(String brand) {
        this.brand = brand.toLowerCase();
    }


    public GameSession(String tenant, String brand, String player, String currency) {
        this.tenant = tenant;
        this.player = player;
        this.brand = brand;
        this.currency = currency;
    }

    public GameSession copy() {

        GameSession newSession = new GameSession();
        newSession.setUid(this.getUid());
        newSession.setTenant(this.getTenant());
        newSession.setPlayerTags(this.getPlayerTags());
        newSession.setPlayer(this.getPlayer());
        newSession.setGame(this.getGame());
        newSession.setPamConnector(this.getPamConnector());
        newSession.setPlayerTags(this.getPlayerTags());
        newSession.setGameConnector(this.getGameConnector());
        newSession.setCorrelationId(this.getCorrelationId());
        newSession.setBrand(this.getBrand());
        newSession.setCasinoUrl(this.getCasinoUrl());
        newSession.setCurrency(this.getCurrency());
        newSession.setDemo(this.isDemo());
        newSession.setStartedAt(this.getStartedAt());
        newSession.setEndedAt(this.getEndedAt());
        newSession.setIpAddress(this.getIpAddress());
        newSession.setJwt(this.getJwt());
        newSession.setLastAccessedAt(this.getLastAccessedAt());
        newSession.setNetwork(this.getNetwork());
        newSession.setProviderGame(this.getProviderGame());
        newSession.setAccount(this.getAccount());
        newSession.setData(this.getData());
        newSession.setStatus(this.getStatus());
        newSession.setDeleted(this.isDeleted());

        newSession.setJurisdiction(this.getJurisdiction());
        newSession.setRealityCheckIntervalInSeconds(this.getRealityCheckIntervalInSeconds());
        newSession.setElapsedTimeInSeconds(this.getElapsedTimeInSeconds());
        newSession.setMinBet(this.getMinBet());
        newSession.setDefaultBet(this.getDefaultBet());
        newSession.setMaxTotalBet(this.getMaxTotalBet());

        return newSession;
    }

    @JsonIgnore
    public String getLobbyUrl() {
        return getUrls().get("lobbyUrl");
    }

    @JsonIgnore
    public String getDepositUrl() {
        return getUrls().get("depositUrl");
    }

    @JsonIgnore
    public String getHistoryUrl() {
        return getUrls().get("historyUrl");
    }

    @JsonIgnore
    public String getOverlayUrl() {
        return getUrls().get("overlayUrl");
    }
}
