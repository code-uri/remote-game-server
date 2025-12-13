package aimlabs.gaming.rgs.players;

import aimlabs.gaming.rgs.players.Player;
import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.core.IEntityService;
import aimlabs.gaming.rgs.core.dto.SearchResponse;
import aimlabs.gaming.rgs.gamesessions.GameSession;

import java.util.List;

public interface IPlayerService extends IEntityService<Player> {
    Player registerOrUpdate(Player player);

    PlayerWallet getBalance(GameSession gameSession, String playerCorrelationId);

    Player findPlayerByNetworkAndCorrelationId(String network, String correlationId);

    Player findOneByUid(String uid);
    // GameSession createPlayerSession(GameSession playerSessionRequest, Brand
    // brand);

    public PlayerInfo initialise(String network, String token, String playerCorrelationId, String currency,
            String brand,
            String gameId, boolean newSessionPerGameLaunch);

    public void updateBalance(String player, PlayerWallet wallet);

    public List<Player> findPlayerByTags(String tenant, List<String> playerTags);

    public Player registerOrUpdate(String network, String brand, String correlationId, List<String> tags);
}
