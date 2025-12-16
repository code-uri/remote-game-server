package aimlabs.gaming.rgs.gamesupplier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import aimlabs.gaming.rgs.brandgames.IBrandGameService;
import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gamerounds.IGameRoundService;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gamesessions.IGameSessionService;
import aimlabs.gaming.rgs.gameskins.GameLaunchRequest;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.players.IPlayerService;
import aimlabs.gaming.rgs.players.PlayerInfo;
import aimlabs.gaming.rgs.settings.GameSettingsService;
import lombok.extern.slf4j.Slf4j;

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
        return new DefaultGameSupplier(connector);
    }

    public class DefaultGameSupplier implements IGameSupplierService {

        private final Connector connector;

        DefaultGameSupplier(Connector connector) {
            this.connector = connector;
        }

        @Override
        public URI launchGame(GameLaunchRequest glr,
                                    String player,
                                    String currency,
                                    GameSkin gameSkin,
                                    String gameConfiguration,
                                    Brand brand) {

            PlayerInfo playerInfo = playerService.initialise(brand.getNetwork(),
                    glr.getToken(),
                    null,
                    glr.getCurrency(),
                    brand.getUid(),
                    gameSkin.getUid(),
                    true);


            Map<String, Object> settings = gameSettingsService.findGameSettingsForCurrency(gameSkin.getTenant(),
                    brand.getUid(),
                    gameSkin.getUid(),
                    playerInfo.getWallet().getCurrency());

            Boolean unfinishedGameExists = gameRoundService.isUnfinishedGameRoundExists(playerInfo.getUid(),
                    glr.getGameId());

            if (!unfinishedGameExists && brand.getStatus() == Status.INACTIVE) {
                throw new BaseRuntimeException(SystemErrorCode.INACTIVE_GAME);
            }

            String gc = (String) settings.getOrDefault("gameConfiguration",
                    gameSkin.getGameConfiguration());

            GameSession gameSession = gameSessionService.findOneByToken(glr.getToken());

            if (gameSession == null) {
                gameSession = gameSessionService.createGameSession(glr,
                        playerInfo.getUid(),
                        playerInfo.getWallet().getCurrency(),
                        gameSkin,
                        gc,
                        brand,
                        gameSkin.getTenant(),
                        null);
            } else {
                gameSession = gameSessionService.updatePartial(gameSession.getUid(), Map.of("game", glr.getGameId(),
                        "gameConfiguration", gameSkin.getGameConfiguration()));
            }

            // glr.setGameId(gameSkin.getName());

            return URI.create(
                    (gameSkin.getUrl() == null ? "http://localhost:8080/games/mr-roboto/index.html"
                            : gameSkin.getUrl()) +
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
        public URI replayGameRound(GameSession gameSession, String gameRound, GameSkin gameSkin, Brand brand) {

            return URI.create(gameSkin.getUrl() +
                    "?gameRound=" + gameRound +
                    "&game=" + gameSession.getProviderGame() +
                    "&brand=" + brand.getUid() +
                    "&preview=true");
        }


        @Override
        public Connector getConnector() {
            return this.connector;
        }

    }

}