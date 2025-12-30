package aimlabs.gaming.rgs.gconnect.provider;

import aimlabs.gaming.rgs.promotions.FreeSpinsPromotionRequest;
import aimlabs.gaming.rgs.promotions.FreeSpinsPromotionResponse;
import aimlabs.gaming.rgs.promotions.IGameSupplierPromotionsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameLaunchRequest;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.players.IPlayerService;
import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.gamesessions.IGameSessionService;
import aimlabs.gaming.rgs.gamesupplier.IGameSupplierService;
import aimlabs.gaming.rgs.settings.IGameSettingsService;
import aimlabs.gaming.rgs.gamesupplier.GameSupplierServiceFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Getter
@Service
public class PhotonGameSupplierFactory implements GameSupplierServiceFactory {

    @Autowired
    IPlayerService playerService;

    @Autowired
    IBrandService brandService;

    @Autowired
    IGameSessionService gameSessionService;

    @Autowired
    ObjectMapper objectMapper;


    @Autowired
    RestClient.Builder restClientBuilder;

    /*
     * @Autowired
     * GravitonGameSupplier gravitonGameSupplier;
     */

    public static String connectorUid = "photon-connector";

    @Autowired
    IGameSettingsService gameSettingsService;

    public boolean supports(Connector connector) {
        return connectorUid.equals(connector.getUid())
                || connectorUid.equals(connector.getParentConnector());
    }

    @Override
    public IGameSupplierService getInstance(Connector connector) {
        return new PhotonGameSupplierConnector(connector);
    }

    private class PhotonGameSupplierConnector implements IGameSupplierService, IGameSupplierPromotionsService {
        private final Connector connector;
        private final RestClient restClient;

        public PhotonGameSupplierConnector(Connector connector) {
            this.connector = connector;
            Object clientId = connector.getSettings().getOrDefault("x-client-id", "default");
            Object clientKey = connector.getSettings().getOrDefault("x-client-key", "default");
            this.restClient = restClientBuilder.baseUrl(connector.getBaseUrl())
                    .defaultHeader("X-Client-ID", String.valueOf(clientId))
                    .defaultHeader("X-Client-Key", String.valueOf(String.valueOf(clientKey)))
                    .build();
            log.info("PhotonGameSupplierConnector create {}", connector);
        }

//        private String baseUrl() {
//            // Allow override from connector settings if provided
//            Object override = connector.getSettings().get("promotionsBaseUrl");
//            if (override instanceof String && !((String) override).isBlank())
//                return (String) override;
//            return connector.getBaseUrl();
//        }

        private HttpHeaders authHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Object clientId = connector.getSettings().getOrDefault("x-client-id", "default");
            Object clientKey = connector.getSettings().getOrDefault("x-client-key", "default");
            headers.set("X-Client-ID", String.valueOf(clientId));
            headers.set("X-Client-Key", String.valueOf(clientKey));
            return headers;
        }

        @Override
        public URI launchGame(GameLaunchRequest glr,
                String player,
                String currency,
                GameSkin gameSkin,
                String gameConfiguration,
                Brand brand) {

            Map<String, Object> settings = gameSettingsService.getBrandGameSettings(brand.getTenant(),
                    brand.getUid(),
                    gameSkin.getUid());

            String gameProviderUrl = (String) settings.getOrDefault("gameProviderUrl", connector.getBaseUrl());
            boolean isDemoGame = glr.getToken() != null && glr.getToken().toLowerCase().startsWith("demo");

            String outGoingGameId = gameSkin.getProviderGame();
            String launchPath = (String) connector.getSettings().get("gameLaunchPath");

            Boolean useParentBrand = Optional.ofNullable(connector.getSettings().get("use-parent-brand"))
                    .map(o -> Boolean.parseBoolean((String) o)).orElse(false);
            String outGoingBrandId = useParentBrand ? Objects.requireNonNull(brand.getParent()) : brand.getUid();

            if (isDemoGame){
                return URI
                        .create(gameProviderUrl + launchPath + "/" + glr.getToken() + "?brand=" + outGoingBrandId + "&gameId="
                                + outGoingGameId + "&token=" + glr.getToken());
            }
                
            boolean alwaysNewSession = glr.isInitSession() || connector.getSettings().containsKey("always-new-session");
            var gameSession = gameSessionService.createGameSessionForGameLaunchRequest(glr, player, currency, gameSkin,
                    gameConfiguration, brand, brand.getTenant(),  alwaysNewSession);

            glr.setGameId(outGoingGameId);

            return URI.create(gameProviderUrl + launchPath + "/" + gameSession.getUid() + "?brand=" + outGoingBrandId +
                    "&gameId=" + outGoingGameId +
                    "&token=" + gameSession.getUid() +
                    "&lang=" + glr.getLanguage() +
                    "&lobbyUrl=" + UriUtils.encode(gameSession.getLobbyUrl(), StandardCharsets.UTF_8) +
                    "&depositUrl=" + UriUtils.encode(gameSession.getDepositUrl(), StandardCharsets.UTF_8) +
                    "&historyUrl=" + UriUtils.encode(gameSession.getHistoryUrl(), StandardCharsets.UTF_8) +
                    "&overlayUrl=" + UriUtils.encode(gameSession.getOverlayUrl(), StandardCharsets.UTF_8) +
                    "&gamePlayMode=" + glr.getGamePlayMode());

        }

        @Override
        public URI replayGameRound(GameSession gameSession, String gameRound, GameSkin gameSkin, Brand brand) {

            Map<String, Object> settings = gameSettingsService.getBrandGameSettings(brand.getTenant(),
                    brand.getUid(),
                    gameSkin.getUid());

            String gameProviderUrl = (String) settings.getOrDefault("gameProviderUrl", connector.getBaseUrl());

            return URI.create(gameProviderUrl + "/games/replay/round?roundId=" + gameRound);
        }


        @Override
        public Connector getConnector() {
               return this.connector;
        }

        // Blocking implementation of promotions API using RestTemplate
        @Override
        public FreeSpinsPromotionResponse awardBonus(FreeSpinsPromotionRequest freeSpinsPromotionRequest) {
            String url = "/games/promotions/award";
            return restClient.post()
                    .uri(url)
                    .headers(httpHeaders -> authHeaders())
                    .body(freeSpinsPromotionRequest)
                    .retrieve()
                    .body(FreeSpinsPromotionResponse.class);
        }

        @Override
        public FreeSpinsPromotionResponse getPromotionByRefId(FreeSpinsPromotionRequest request) {
            String url = "/games/promotions/" + request.getPromotionRefId();
            return restClient.get()
                    .uri(url)
                    .headers(httpHeaders -> authHeaders())
                    .retrieve()
                    .body(FreeSpinsPromotionResponse.class);
        }

        @Override
        public FreeSpinsPromotionResponse cancelBonus(String promotionRefId) {
            String url = "/games/promotions/" + promotionRefId + "/cancel";
            return restClient.get()
                    .uri(url)
                    .headers(httpHeaders -> authHeaders())
                    .retrieve()
                    .body(FreeSpinsPromotionResponse.class);
        }
    }
}
