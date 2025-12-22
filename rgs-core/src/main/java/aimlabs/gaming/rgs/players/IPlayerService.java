package aimlabs.gaming.rgs.players;

import java.util.List;

import aimlabs.gaming.rgs.core.IEntityService;
import aimlabs.gaming.rgs.gamesessions.GameSession;

public interface IPlayerService extends IEntityService<Player> {
    Player saveOrUpdate(Player player);

    PlayerWallet getBalance(GameSession gameSession, String playerCorrelationId);

    Player findByCorrelationidAndNetworkAndBrand(String network, String brand, String correlationId);

    Player findOneByUid(String uid);
    // GameSession createPlayerSession(GameSession playerSessionRequest, Brand
    // brand);

    public PlayerInfo initialise(String network, String token, String playerCorrelationId, String currency,
            String brand,
            String gameId, boolean newSessionPerGameLaunch);

    public void updateBalance(String player, PlayerWallet wallet);

    public List<Player> findPlayerByTags(String tenant, List<String> playerTags);

    public Player findAndUpdatePlayerTagsByCorrelationidAndNetworkAndBrand(String network, String brand, String correlationId, List<String> tags);
}
    