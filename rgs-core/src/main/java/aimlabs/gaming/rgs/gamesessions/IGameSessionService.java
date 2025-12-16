package aimlabs.gaming.rgs.gamesessions;

import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.core.IEntityService;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.gameskins.GameLaunchRequest;
import aimlabs.gaming.rgs.gameskins.GameSkin;

import java.util.Map;

public interface IGameSessionService extends IEntityService<GameSession> {
    GameSession createGameSession(GameSession gameSession);

    GameSession findOneByUid(String uid);

    GameSession findLastOneByPlayer(String player);

    GameSession findOneByUidAndStatus(String uid, Status status);

    GameSession findOneByGameConnectorAndCorrelationIdAndStatus(String gameConnector, String correlationId,
            Status status);

    GameSession findOneByToken(String token);

    GameSession findOneByTokenAndStatus(String token, Status status);

    Boolean keepSessionAlive(GameSession session);

    Boolean setExpiration(GameSession session);

    GameSession expireSession(GameSession gameSession);

    GameSession createGameSession(GameLaunchRequest glr, String player, String currency, GameSkin gameSkin,
            String gameConfiguration, Brand brand, String tenant, String correlationId);

    GameSession createGameSession(GameSession gameSession, String gameId, String player, String currency, String token);

    GameSession update(GameSession obj);

    GameSession updatePartial(String uid, Map<String, Object> values);

    void updateStatus(String key, Status status);

    GameSession findOneByUidAcrossTenants(String uid);

    GameSession findOneByTokenAndUpdateGame(String externalToken, String gameId, String gameConfiguration);

    GameSession createGameSessionForGameLaunchRequest(GameLaunchRequest glr, String player, String currency, GameSkin gameSkin,
            String gameConfiguration, Brand brand, String tenant, boolean alwaysNewSession);
}
