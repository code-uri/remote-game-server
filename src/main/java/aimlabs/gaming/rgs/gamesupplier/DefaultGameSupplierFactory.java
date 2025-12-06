package aimlabs.gaming.rgs.gamesupplier;

import aimlabs.gaming.rgs.brandgames.BrandGameAggregate;
import aimlabs.gaming.rgs.brandgames.IBrandGameService;
import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.gamerounds.IGameRoundService;
import aimlabs.gaming.rgs.games.GameLaunchRequest;
import aimlabs.gaming.rgs.games.GameSupplierServiceFactory;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gamesessions.IGameSessionService;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.players.IPlayerService;
import aimlabs.gaming.rgs.players.PlayerInfo;
import aimlabs.gaming.rgs.settings.GameSettingsService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
public class DefaultGameSupplierFactory implements GameSupplierServiceFactory {

    @Autowired
    IGameSessionService gameSessionService;

    @Autowired
    GameSettingsService gameSettingsService;

    @Autowired
    IPlayerService playerService;

    @Autowired
    IGameRoundService gameRoundService;

    @Autowired
    IBrandGameService brandGameService;

    // TODO fix this.
    // @Autowired
    // IPromotionService promotionService;

    DefaultGameSupplierFactory() {
        log.info("DefaultGameSupplierFactory initialized");
    }

    @Override
    public boolean supports(Connector connector) {
        return connector == null || connector.getUid() == null || "local-connector".equals(connector.getUid());
    }

    @Override
    public IGameSupplierService getInstance(Connector connector) {
        return new DefaultGameSupplier();
    }

    public class DefaultGameSupplier implements IGameSupplierService {

        @Override
        public URI launchGame(GameLaunchRequest glr) {

            PlayerInfo playerInfo = playerService.initialise(glr.getNetwork(),
                    glr.getToken(),
                    null,
                    glr.getCurrency(),
                    glr.getBrand(),
                    glr.getGameId(),
                    true);

            BrandGameAggregate brandGameAggregate = brandGameService.findOneByNetworkAndBrandAndGameId(glr.getNetwork(),
                    glr.getBrand(), glr.getGameId());
            Brand brand = brandGameAggregate.brand();
            GameSkin game = brandGameAggregate.game();

            Map<String, Object> settings = gameSettingsService.findGameSettingsForCurrency(game.getTenant(),
                    brand.getUid(),
                    game.getUid(),
                    playerInfo.getWallet().getCurrency());

            Boolean unfinishedGameExists = gameRoundService.isUnfinishedGameRoundExists(playerInfo.getUid(),
                    glr.getGameId());

            if (!unfinishedGameExists && brandGameAggregate.status() == Status.INACTIVE) {
                throw new BaseRuntimeException(SystemErrorCode.INACTIVE_GAME);
            }

            String gc = (String) settings.getOrDefault("gameConfiguration",
                    brandGameAggregate.game().getGameConfiguration());

            GameSession gameSession = gameSessionService.findOneByToken(glr.getToken());

            if (gameSession == null) {
                gameSession = gameSessionService.createGameSession(glr,
                        playerInfo.getUid(),
                        playerInfo.getWallet().getCurrency(),
                        brandGameAggregate.game(),
                        gc,
                        brandGameAggregate.brand(),
                        game.getTenant(),
                        null);
            } else {
                gameSession = gameSessionService.updatePartial(gameSession.getUid(), Map.of("game", glr.getGameId(),
                        "gameConfiguration", brandGameAggregate.game().getGameConfiguration()));
            }

            // glr.setGameId(gameSkin.getName());

            return URI.create(
                    (brandGameAggregate.game().getUrl() == null ? "http://localhost:8080/games/mr-roboto/index.html"
                            : brandGameAggregate.game().getUrl()) +
                            "?token=" + gameSession.getUid() +
                            "&brand=" + glr.getBrand() +
                            "&lang=" + glr.getLanguage() +
                            "&lobbyUrl=" + UriUtils.encode(gameSession.getLobbyUrl(), StandardCharsets.UTF_8) +
                            "&depositUrl=" + UriUtils.encode(gameSession.getDepositUrl(), StandardCharsets.UTF_8) +
                            "&historyUrl=" + UriUtils.encode(gameSession.getHistoryUrl(), StandardCharsets.UTF_8) +
                            "&overlayUrl=" + UriUtils.encode(gameSession.getOverlayUrl(), StandardCharsets.UTF_8) +
                            "&gamePlayMode=" + glr.getGamePlayMode());
        }

        @Override
        public URI replayGameRound(GameSession gameSession, GameRound gameRound, GameSkin gameSkin, Brand brand) {

            return URI.create(gameSkin.getUrl() +
                    "?gameRound=" + gameRound.getUid() +
                    "&game=" + gameRound.getGameId() +
                    "&brand=" + brand.getUid() +
                    "&preview=true");
        }

    }

}