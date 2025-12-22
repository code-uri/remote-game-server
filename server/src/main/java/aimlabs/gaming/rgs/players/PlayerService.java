package aimlabs.gaming.rgs.players;

import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gameoperators.*;
import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gamesessions.IGameSessionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Data
@Service
public class PlayerService extends AbstractEntityService<Player, PlayerDocument> implements IPlayerService {

    @Autowired
    PlayerStore store;

    @Autowired
    PlayerMapper mapper;

    @Autowired
    IBrandService brandService;

    @Autowired
    IGameSessionService gameSessionService;

    @Autowired
    PlayerAccountManager playerAccountManager;

    @Autowired
    @Qualifier("demo")
    PlayerAccountManager demoPlayerServiceAdapter;

    @Override
    public Player saveOrUpdate(Player player) {
        Player existingPlayer = findByCorrelationidAndNetworkAndBrand(player.getNetwork(), player.getBrand(), player.getCorrelationId());
        if(existingPlayer == null)
            return super.create(player);
        else if(player.getTags()!=null){
                existingPlayer.setTags(player.getTags());
                return super.updatePartial(existingPlayer.getUid(), Map.of("tags", player.getTags()));
        }
        else
            return existingPlayer;
    }

    public PlayerWallet getBalance(GameSession playerSession, String player) {
        PlayerBalanceRequest playerBalanceRequest = new PlayerBalanceRequest();
        playerBalanceRequest.setTenant(playerSession.getTenant());
        playerBalanceRequest.setBrand(playerSession.getBrand());
        playerBalanceRequest.setToken(playerSession.getToken());
        playerBalanceRequest.setPlayer(player);
        playerBalanceRequest.setCurrency(playerSession.getCurrency());
        playerBalanceRequest.setGameId(playerSession.getGame());
        if (playerSession.isDemo()) {
            Wallet as = demoPlayerServiceAdapter.playerBalance(playerBalanceRequest);
            return PlayerWalletUtils.asPlayerWallet(as);
        }

        return PlayerWalletUtils.asPlayerWallet(playerAccountManager.playerBalance(playerBalanceRequest));
    }

    public Player findByCorrelationidAndNetworkAndBrand(String network, String brand, String correlationId) {
        return store.findOneByNetworkAndBrandAndCorrelationId(network, brand, correlationId);
    }

    public Player findOneByUid(String uid) {
        return getMapper().asDto(this.getStore().findOneByUid(uid));

    }

    /*
     * public GameSession createPlayerSession(GameSession playerSessionRequest,
     * Brand brand) {
     * Player player = new Player();
     * player.setCorrelationId(playerSessionRequest.getPlayer());
     * player.setNetwork(brand.getNetwork());
     * //player.setRealmType(brand.getRealmType());
     * player.setTenant(playerSessionRequest.getTenant());
     * player.setBrand(playerSessionRequest.getBrand());
     * player.setTags(playerSessionRequest.getPlayerTags());
     * if (!playerSessionRequest.isDemo()) {
     * return registerOrUpdate(player)
     * .flatMap(playerSaved -> {
     * playerSessionRequest.setPlayer(playerSaved.getUid());
     * playerSessionRequest.setToken(UUID.randomUUID().toString());
     * return gameSessionService.createGameSession(playerSessionRequest);
     * });
     * } else {
     * return gameSessionService.createGameSession(playerSessionRequest);
     * }
     * }
     */

    @Override
    public PlayerInfo initialise(String network, String token, String playerCorrelationId, String currency,
            String brand,
            String gameId, boolean newSessionPerGameLaunch) {

        boolean demo = token != null && token.toLowerCase().startsWith("demo");

        // createToken is used for initialise call from game supplier context.
        // will be using externalSession for checking the validity of the player from
        // aggregator context.
        // String createToken = gameSupplierToken!=null?gameSupplierToken
        // :externalToken;

        // String playerExternalToken =
        // aggregatorGameSession!=null?aggregatorGameSession.getToken():externalToken;
        PlayerInfo playerInfo = new PlayerInfo();
        try {
            String tenant = TenantContextHolder.getTenant();
            PlayerInitialiseRequest initialiseRequest = new PlayerInitialiseRequest();
            initialiseRequest.setTenant(tenant);
            initialiseRequest.setBrand(brand);

            initialiseRequest.setGameId(gameId);
            initialiseRequest.setSessionToken(token);
            initialiseRequest.setPlayer(playerCorrelationId);
            initialiseRequest.setCurrency(currency);
            // return getBalance(gameSession, player, gameId)
            PlayerInitialiseResponse playerInitialiseResponse;
            if (demo)
                playerInitialiseResponse = demoPlayerServiceAdapter.playerInitialise(initialiseRequest);
            else
                playerInitialiseResponse = playerAccountManager.playerInitialise(initialiseRequest);

            // info.setUid(player.getUid());
            playerInfo.setPlayer(playerInitialiseResponse.getPlayerId());
            playerInfo.setExternalToken(playerInitialiseResponse.getExternalToken() != null
                    ? playerInitialiseResponse.getExternalToken()
                    : token);
            playerInfo.setWallet(PlayerWalletUtils
                    .asPlayerWallet(playerInitialiseResponse.getWallet()));
            playerInfo.setTags(playerInitialiseResponse.getTags());
            playerInfo.setSupportsMultiCredits(playerInitialiseResponse.isSupportsMultiCredits());

            if (!demo) {

                String correlationId = (playerInfo.getPlayer() != null ? playerInfo.getPlayer()
                        : playerInfo.getExternalToken());

                Player player = findAndUpdatePlayerTagsByCorrelationidAndNetworkAndBrand(network, brand, correlationId,
                        playerInfo.getTags());

                playerInfo.setUid(player.getUid());
                return playerInfo;
            } else {
                playerInfo.setUid(playerInfo.getPlayer());
            }

        } catch (Exception e) {
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "player initialize failed!", e);
        }

        return playerInfo;
    }

    public void updateBalance(String player, PlayerWallet wallet) {

        if (wallet != null && wallet.getTotalAvailable() != null && wallet.getTotalAvailable().isPositive()) {
            getStore().getTemplate().findAndModify(Query.query(Criteria.where("uid").is(player)
                    .and("deleted").is(false)), Update.update("wallet", wallet), PlayerDocument.class);
        }
    }

    /*
     * public Player findOneByUidAcrossTenants(String partnerUserId) {
     * return getStore().getTemplate().findOne(Query.query(Criteria.where("uid").is(
     * partnerUserId).and("deleted").is(false)),
     * PlayerDocument.class).map(mapper::asDto);
     * }
     */

    public List<Player> findPlayerByTags(String tenant, List<String> playerTags) {
        return getStore().getTemplate().find(Query.query(Criteria.where("tenant").is(tenant)
                .and("deleted").is(false).and("tags").in(playerTags)), PlayerDocument.class)
                .stream().map(e -> getMapper().asDto(e)).toList();
    }

    public Player findAndUpdatePlayerTagsByCorrelationidAndNetworkAndBrand(String network, String brand,
            String correlationId, List<String> tags) {
        Player player = new Player();
        player.setNetwork(network);
        player.setBrand(brand);
        player.setTenant(TenantContextHolder.getTenant());
        player.setCorrelationId(correlationId);
        player.setTags(tags);
        return saveOrUpdate(player);
    }
}
