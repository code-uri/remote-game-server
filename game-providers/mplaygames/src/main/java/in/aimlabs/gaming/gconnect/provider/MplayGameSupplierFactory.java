package in.aimlabs.gaming.gconnect.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.gameskins.GameLaunchRequest;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.players.IPlayerService;
import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.gamesessions.IGameSessionService;
import aimlabs.gaming.rgs.gamesupplier.IGameSupplierService;
import aimlabs.gaming.rgs.gamesupplier.GameSupplierServiceFactory;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
                Brand brand,
                Connector connector) {

            boolean isDemoGame = glr.getToken() != null && glr.getToken().toLowerCase().startsWith("demo");
            String providerGame = gameSkin.getProviderGame();
            String launchPath = (String) connector.getSettings().get("gameLaunchPath");

            Boolean useParentBrand = Optional.ofNullable(connector.getSettings().get("use-parent-brand"))
                    .map(o -> Boolean.parseBoolean((String) o)).orElse(false);
            String brandUid = useParentBrand ? Objects.requireNonNull(brand.getParent()) : brand.getUid();

            if (isDemoGame) {
                return URI.create(connector.getBaseUrl() + launchPath + "/" + glr.getToken()
                        + "?brand=" + brandUid
                        + "&gameId=" + providerGame
                        + "&token=" + glr.getToken()
                        + "&lang=" + glr.getLanguage()
                        + "&" + getUrlQueryString(glr.getParams()));
            }

            // TODO: Get tenant from context - for now using default
            String tenant = "default";

            Brand brandMapped = brandService.findOneByTenantAndBrand(tenant, brandUid);
            if (brandMapped == null) {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_BRAND,
                        String.format("invalid game launch request with brand %s. force-parent-brand %s", brandUid,
                                useParentBrand));
            }

            var gameSession = (glr.isInitSession() || connector.getSettings().containsKey("always-new-session"))
                    ? gameSessionService.createGameSession(glr, player, currency, gameSkin, gameConfiguration,
                            brandMapped, tenant, null)
                    : Optional.ofNullable(gameSessionService.findOneByToken(glr.getToken()))
                            .map(gs -> gameSessionService.updatePartial(gs.getUid(),
                                    Map.of("game", gameSkin.getUid(), "providerGame", gameSkin.getProviderGame())))
                            .orElseGet(() -> gameSessionService.createGameSession(glr, player, currency, gameSkin,
                                    gameConfiguration, brandMapped, tenant, null));

            glr.setGameId(gameSkin.getProviderGame());
            return URI.create(connector.getBaseUrl() + launchPath + "/" + gameSession.getUid()
                    + "?brand=" + brandUid
                    + "&gameId=" + providerGame
                    + "&token=" + gameSession.getUid()
                    + "&lang=" + glr.getLanguage()
                    + "&lobbyUrl=" + UriUtils.encode(gameSession.getLobbyUrl(), StandardCharsets.UTF_8)
                    + "&lobbyURL=" + UriUtils.encode(gameSession.getLobbyUrl(), StandardCharsets.UTF_8)
                    + "&depositUrl=" + UriUtils.encode(gameSession.getDepositUrl(), StandardCharsets.UTF_8)
                    + "&depositURL=" + UriUtils.encode(gameSession.getDepositUrl(), StandardCharsets.UTF_8)
                    + "&historyUrl=" + UriUtils.encode(gameSession.getHistoryUrl(), StandardCharsets.UTF_8)
                    + "&historyURL=" + UriUtils.encode(gameSession.getHistoryUrl(), StandardCharsets.UTF_8)
                    + "&overlayUrl=" + UriUtils.encode(gameSession.getOverlayUrl(), StandardCharsets.UTF_8)
                    + "&gamePlayMode=" + glr.getGamePlayMode());
        }
    }

    private String getUrlQueryString(Map<String, String> launchInfo) {
        launchInfo.remove("tenant");
        launchInfo.remove("gameId");
        launchInfo.remove("token");
        return launchInfo.entrySet()
                .stream()
                .map(entry -> UriUtils.encode(entry.getKey(), StandardCharsets.UTF_8.toString()) + "=" +
                        UriUtils.encode(entry.getValue(), StandardCharsets.UTF_8.toString()))
                .collect(Collectors.joining("&"));
    }
}