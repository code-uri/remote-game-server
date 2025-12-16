package in.aimlabs.gaming.gconnect.provider;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.gamesessions.IGameSessionService;
import aimlabs.gaming.rgs.gameskins.GameLaunchRequest;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.gamesupplier.GameSupplierServiceFactory;
import aimlabs.gaming.rgs.gamesupplier.IGameSupplierService;
import aimlabs.gaming.rgs.players.IPlayerService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Service
public class MplayGameSupplierFactory implements GameSupplierServiceFactory {

        @Autowired
        IPlayerService playerService;

        @Autowired
        IBrandService brandService;

        @Autowired
        IGameSessionService gameSessionService;

        @Autowired
        ObjectMapper objectMapper;

        public static String connectorUid = "mplay-connector";

        public boolean supports(Connector connector) {
                return connectorUid.equals(connector.getUid())
                                || connectorUid.equals(connector.getParentConnector());
        }

        @Override
        public IGameSupplierService getInstance(Connector connector) {
                return new MplayGameSupplierConnector(connector);
        }

        private class MplayGameSupplierConnector implements IGameSupplierService {
                private final Connector connector;

                public MplayGameSupplierConnector(Connector connector) {
                        this.connector = connector;
                        log.info("MplayGameSupplierConnector create {}", connector);
                }

                @Override
                public URI launchGame(GameLaunchRequest glr,
                                String player,
                                String currency,
                                GameSkin gameSkin,
                                String gameConfiguration,
                                Brand brand) {

                        boolean isDemoGame = glr.getToken() != null && glr.getToken().toLowerCase().startsWith("demo");
                        String outGoingGameId = gameSkin.getProviderGame();
                        String launchPath = (String) connector.getSettings().get("gameLaunchPath");

                        Boolean useParentBrand = Optional.ofNullable(connector.getSettings().get("use-parent-brand"))
                                        .map(o -> Boolean.parseBoolean((String) o)).orElse(false);
                        String outGoingBrandId = useParentBrand ? Objects.requireNonNull(brand.getParent()) : brand.getUid();

                        if (isDemoGame) {
                                return URI.create(connector.getBaseUrl() + launchPath + "/" + glr.getToken()
                                                + "?brand=" + outGoingBrandId
                                                + "&gameId=" + outGoingGameId
                                                + "&token=" + glr.getToken()
                                                + "&lang=" + glr.getLanguage());
                        }

                        boolean alwaysNewSession = glr.isInitSession()
                                        || connector.getSettings().containsKey("always-new-session");
                        var gameSession = gameSessionService.createGameSessionForGameLaunchRequest(glr, player,
                                        currency, gameSkin, gameConfiguration, brand, brand.getTenant(),
                                        alwaysNewSession);
                        glr.setGameId(outGoingGameId);
                        return URI.create(connector.getBaseUrl() + launchPath + "/" + gameSession.getUid()
                                        + "?brand=" + outGoingBrandId
                                        + "&gameId=" + outGoingGameId
                                        + "&token=" + gameSession.getUid()
                                        + "&lang=" + glr.getLanguage()
                                        + "&lobbyUrl="
                                        + UriUtils.encode(gameSession.getLobbyUrl(), StandardCharsets.UTF_8)
                                        + "&lobbyURL="
                                        + UriUtils.encode(gameSession.getLobbyUrl(), StandardCharsets.UTF_8)
                                        + "&depositUrl="
                                        + UriUtils.encode(gameSession.getDepositUrl(), StandardCharsets.UTF_8)
                                        + "&depositURL="
                                        + UriUtils.encode(gameSession.getDepositUrl(), StandardCharsets.UTF_8)
                                        + "&historyUrl="
                                        + UriUtils.encode(gameSession.getHistoryUrl(), StandardCharsets.UTF_8)
                                        + "&historyURL="
                                        + UriUtils.encode(gameSession.getHistoryUrl(), StandardCharsets.UTF_8)
                                        + "&overlayUrl="
                                        + UriUtils.encode(gameSession.getOverlayUrl(), StandardCharsets.UTF_8)
                                        + "&gamePlayMode=" + glr.getGamePlayMode());
                }

                @Override
                public Connector getConnector() {
                       return this.connector;
                }
        }
}