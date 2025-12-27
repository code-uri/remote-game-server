package aimlabs.gaming.rgs.gamesessions;

import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.core.utils.JwtUtil;
import aimlabs.gaming.rgs.currency.Currency;
import aimlabs.gaming.rgs.currency.ICurrencyService;
import aimlabs.gaming.rgs.gameskins.GameLaunchRequest;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.money.CurrencyUnit;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT(JSON Web Token) Backed Player GameSessionService.
 */
@Slf4j
@Data
@Service
public class GameSessionService extends AbstractEntityService<GameSession, GameSessionDocument>
        implements IGameSessionService {

    @Autowired
    IBrandService brandService;
    @Value("${app.player.game-session.expiration.secs:900}")
    private int gameSessionExpirationSecs;
    @Value("${app.operator.init-session.expiration.secs:120}")
    private Integer initSessionExpirationSecs;

    @Autowired
    ICurrencyService currencyService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    GameSessionStore store;
    @Autowired
    GameSessionMapper mapper;

    public GameSession createGameSession(GameSession gameSession) {
        Brand brand = brandService.findOneByTenantAndBrand(gameSession.getTenant(), gameSession.getBrand());

        if (brand == null)
            throw new BaseRuntimeException(SystemErrorCode.INVALID_BRAND);

        Currency currency;
        if (gameSession.getCurrency() != null) {
            CurrencyUnit currencyUnit = currencyService.getCurrency(gameSession.getCurrency());
            if (currencyUnit == null)
                throw new BaseRuntimeException(SystemErrorCode.CURRENCY_NOT_SUPPORTED);
        }

        GameSession newSession = new GameSession(gameSession.getTenant(), gameSession.getBrand(),
                gameSession.getPlayer(), gameSession.getCurrency());
        newSession.setUid(gameSession.isDemo() ? gameSession.getToken() : UUID.randomUUID().toString());
        newSession.setStartedAt(gameSession.getStartedAt() != null ? gameSession.getStartedAt() : new Date());
        newSession.setNetwork(brand.getNetwork());
        String jwt = jwtUtil.generateJws(new HashMap<>(),
                Map.of("brand", gameSession.getBrand(),
                        "player", gameSession.getPlayer()),
                Duration.ofDays(1));
        newSession.setJwt(jwt);
        newSession.setDemo(gameSession.isDemo());

        newSession.setCasinoUrl(gameSession.getCasinoUrl());
        newSession.setIpAddress(gameSession.getIpAddress());
        newSession.setToken(gameSession.getToken());
        newSession.setGame(gameSession.getGame());
        newSession.setGameConfiguration(gameSession.getGameConfiguration());
        newSession.setGameConnector(gameSession.getGameConnector());
        newSession.setProviderGame(gameSession.getProviderGame());
        newSession.setPamConnector(brand.getConnectorUid());
        newSession.setStatus(Status.ACTIVE);
        newSession.setUrls(gameSession.getUrls());

        newSession.setJurisdiction(gameSession.getJurisdiction());
        if (brand.getJurisdiction() != null && newSession.getJurisdiction() == null)
            newSession.setJurisdiction(brand.getJurisdiction());

         if(newSession.getJurisdiction()!=null)
        {
            newSession.setRealityCheckIntervalInSeconds(gameSession.getRealityCheckIntervalInSeconds());
            if (brand.getRealityCheckIntervalInMilliSeconds() > 0 && newSession.getRealityCheckIntervalInSeconds() == 0)
                newSession.setRealityCheckIntervalInSeconds(Duration.ofMillis(brand.getRealityCheckIntervalInMilliSeconds()).toSeconds());

            newSession.setElapsedTimeInSeconds(gameSession.getElapsedTimeInSeconds());
        }

        getMapper().asDto(getStore().create(getMapper().asEntity(newSession)));
        setExpiration(newSession.getUid(), newSession.getCreatedOn().getTime());
        return newSession;
    }

    public GameSession findLastOneByPlayer(String player) {
        return getMapper().asDto(getStore().findLastOneByPlayer(player));
    }

    public GameSession findOneByUidAndStatus(String uid, Status status) {
        return getMapper().asDto(store.findOneByUidAndStatus(uid, status));
    }

    public GameSession findOneByGameConnectorAndCorrelationIdAndStatus(String gameConnector, String correlationId,
            Status status) {
        return getMapper()
                .asDto(store.findOneByGameConnectorAndCorrelationIdAndStatus(gameConnector, correlationId, status));
    }

    public GameSession findOneByToken(String token) {
        return getMapper().asDto(store.findOneByToken(token));
    }

    public GameSession findOneByTokenAndStatus(String token, Status status) {
        return getMapper().asDto(store.findOneByTokenAndStatus(token, status));
    }

    public Boolean keepSessionAlive(String uid) {
        if (uid == null) {
            return null;
        }

        RedisTemplate<String, Object> redisTemplate = getRedisTemplate();
        String sessionKey = getSessionKey(uid); // Key = "session-123", Value = startTime

        Object startTimeObj = redisTemplate.opsForValue().get(sessionKey);
        long elapsedTimeInMilliSeconds = startTimeObj instanceof Long ? (Long) startTimeObj : 0L;

        Duration ttl = Duration.ofSeconds(redisTemplate.getExpire(sessionKey));

        if (ttl == null)
            throw new BaseRuntimeException(SystemErrorCode.TOKEN_EXPIRED);

        long currentTime = Instant.now().toEpochMilli();
        elapsedTimeInMilliSeconds = currentTime - elapsedTimeInMilliSeconds; // Time since session creation

        log.info("Keeping session {} alive | Elapsed Time: {} seconds | TTL: {}",
                uid, Duration.ofMillis(elapsedTimeInMilliSeconds).toSeconds(), ttl.toSeconds());

        return setExpiration(uid, elapsedTimeInMilliSeconds);

    }

    private static String getSessionKey(String session) {
        return "session-" + session;
    }

    @Override
    public Boolean setExpiration(String  uid, long expirationInSeconds) {
        String sessionKey = getSessionKey(uid); // Key = "session-123", Value = startTim

        getRedisTemplate()
            .opsForValue().set(sessionKey, expirationInSeconds,
                Duration.ofSeconds(getGameSessionExpirationSecs()));
        return true;
    }

    public GameSession expireSession(GameSession gameSession) {
        String sessionKey = getSessionKey(gameSession.getUid());
        gameSession = updatePartial(sessionKey, Map.of("status", Status.INACTIVE));

        getRedisTemplate()
                .opsForValue().getAndDelete(gameSession.getUid());

        return gameSession;
    }

    public GameSession createGameSession(GameLaunchRequest glr,
            String player,
            String currency,
            GameSkin gameSkin,
            String gameConfiguration,
            Brand brand,
            String tenant,
            String correlationId) {

        boolean isDemoGame = glr.getToken() != null && glr.getToken().toLowerCase().startsWith("demo") || glr.isDemo();

        GameSession newSession = new GameSession(tenant,
                brand.getUid(),
                isDemoGame ? glr.getToken() : player,
                currency);

        newSession.setNetwork(brand.getNetwork());
        newSession.setDemo(isDemoGame);
        newSession.setCasinoUrl(glr.getBrandUrl());
        newSession.setGame(gameSkin.getUid());
        newSession.setProviderGame(gameSkin.getProviderGame());
        newSession.setPamConnector(brand.getConnectorUid());
        newSession.setGameConnector(gameSkin.getConnector());
        newSession.setIpAddress(glr.getIpAddress());
        newSession.setToken(glr.getToken());
        newSession.setCorrelationId(correlationId);
        newSession.setGameConfiguration(gameConfiguration);
        newSession.setUrls(brand.getUrls());

        newSession.setJurisdiction(newSession.getJurisdiction());
        if (brand.getJurisdiction() != null && newSession.getJurisdiction() == null)
            newSession.setJurisdiction(brand.getJurisdiction());

        if (newSession.getJurisdiction() != null) {
             newSession.setRealityCheckIntervalInSeconds(Duration.ofMillis(glr.getRealityCheckIntervalInMilliSeconds()).toSeconds());
            if (brand.getRealityCheckIntervalInMilliSeconds() > 0 && newSession.getRealityCheckIntervalInSeconds() == 0)
                newSession.setRealityCheckIntervalInSeconds(Duration.ofMillis(brand.getRealityCheckIntervalInMilliSeconds()).toSeconds());

            newSession.setElapsedTimeInSeconds(Duration.ofMillis(glr.getElapsedTimeInMilliSeconds()).toSeconds());
        }

        if (glr.getLobbyUrl() != null)
            newSession.getUrls().put("lobbyUrl", glr.getLobbyUrl());
        if (glr.getDepositUrl() != null)
            newSession.getUrls().put("depositUrl", glr.getDepositUrl());
        if (glr.getHistoryUrl() != null)
            newSession.getUrls().put("historyUrl", glr.getHistoryUrl());

        return createGameSession(newSession);
    }

    @Override
    public GameSession createGameSessionForGameLaunchRequest(GameLaunchRequest glr, String player, String currency,
            GameSkin gameSkin, String gameConfiguration, Brand brand, String tenant,
            boolean alwaysNewSession) {

        if (alwaysNewSession) {
            return createGameSession(glr, player, currency, gameSkin, gameConfiguration, brand, tenant, null);
        }

        GameSession existingSession = findOneByToken(glr.getToken());
        if (existingSession != null) {
            return updatePartial(existingSession.getUid(),
                    Map.of("game", gameSkin.getUid(), "providerGame", gameSkin.getProviderGame()));
        }

        return createGameSession(glr, player, currency, gameSkin, gameConfiguration, brand, tenant, null);
    }

    public GameSession createGameSession(GameSession gameSession, String gameId, String player, String currency,
            String token) {

        GameSession newSession = new GameSession(gameSession.getTenant(), gameSession.getBrand(),
                player,
                currency);
        // session.setIpAddress(playerSessionRequest.getIpAddress());
        // if(gameSkin!=null) {
        newSession.setNetwork(gameSession.getNetwork());
        newSession.setGame(gameId);
        newSession.setProviderGame(gameSession.getProviderGame());
        newSession.setPamConnector(gameSession.getPamConnector());
        newSession.setGameConnector(gameSession.getGameConnector());
        newSession.setIpAddress(gameSession.getIpAddress());
        // }

        newSession.setCorrelationId(gameSession.getToken());
        newSession.setToken(token);
        newSession.setGameConfiguration(gameSession.getGameConfiguration());

        newSession.setStartedAt(new Date());

        newSession.setGame(gameSession.getGame());
        newSession.setStatus(Status.ACTIVE);
        newSession.setUrls(gameSession.getUrls());
        newSession.setAggregateCredits(gameSession.isAggregateCredits());

        newSession.setJurisdiction(gameSession.getJurisdiction());
        newSession.setRealityCheckIntervalInSeconds(gameSession.getRealityCheckIntervalInSeconds());
        newSession.setElapsedTimeInSeconds(gameSession.getElapsedTimeInSeconds());

        String jwt = jwtUtil.generateJws(new HashMap<>(),
                Map.of("brand", newSession.getBrand(),
                        "player", newSession.getPlayer()),
                Duration.ofDays(1));
        newSession.setJwt(jwt);

        return create(newSession);
    }

    public GameSession findOneByUidAcrossTenants(String uid) {
        return getMapper().asDto(getStore().getTemplate().findOne(
                Query.query(Criteria.where("uid").is(uid).and("deleted").is(false)), GameSessionDocument.class));
    }

    public GameSession findOneByTokenAndUpdateGame(String externalToken, String gameId, String gameConfiguration) {
        return getMapper().asDto(store.findOneByTokenAndUpdateGame(externalToken, gameId, gameConfiguration));
    }
}
