package aimlabs.gaming.rgs.gameoperators.mockoperator;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.ErrorCode;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gameoperators.*;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.players.Player;
import aimlabs.gaming.rgs.transactions.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/mock")
@Slf4j
@Data
public class MockPAMController {

    @Value("${spring.webflux.base-path:/api/rgs}")
    private String basPath;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MockPlayerServiceAdapter mockPlayerServiceAdapter;

    @Autowired
    MockPlayerRepository mockPlayerRepository;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    private AtomicInteger readTimeOut = new AtomicInteger(3);
    private ConcurrentHashMap<String, String> gameRoundMockErrors = new ConcurrentHashMap<>();


    private Player createPlayer(String tenant, String brand, String playerId, String currency) {
        Player mockPlayer = new Player();
        mockPlayer.setTenant(tenant);
        mockPlayer.setBrand(brand);
        mockPlayer.setUid(playerId + "|" + currency);
        
        // Save to in-memory store
        mockPlayerRepository.save(mockPlayer);

        log.info("Mock mockPlayer {} inserted", mockPlayer);
        
        return mockPlayer;
    }

    /**
     * POST /player-initialise : Initiate player game session. Sends player wallet balance and adhoc settings or regulations.
     *
     * @param request  MockPlayer Initialise Request (required)
     * @return successful operation (status code 200)
     */
    @PostMapping(value = "/player-initialise")
    public ResponseEntity<PlayerInitialiseResponse> playerInitialise(
            @RequestBody PlayerInitialiseRequest request,
            @RequestHeader(defaultValue = "default") String tenant) {
        
        log.info("{}", request);

        Player mockPlayer = mockPlayerRepository.findByCorrelationId(request.getSessionToken());
        if (mockPlayer == null) {
            throw new BaseRuntimeException(MockPlayerConnectErrorCode.INVALID_TOKEN);
        }

        String currency = mockPlayer.getUid().split("\\|")[1];
        GameSession gameSession = new GameSession(mockPlayer.getTenant(), mockPlayer.getBrand(), 
                mockPlayer.getUid(), currency);
        gameSession.setToken(mockPlayer.getCorrelationId());
        gameSession.setCurrency(currency);
        gameSession.setBrand(mockPlayer.getBrand());

        PlayerInitialiseResponse playerInfo = mockPlayerServiceAdapter.playerInitialise(gameSession, request.getGameId());
        
        PlayerInitialiseResponse response = new PlayerInitialiseResponse();
        response.setPlayerId(playerInfo.getPlayerId());
        response.unWrapWallet(playerInfo.getWallet());
        response.setExternalToken(mockPlayer.getCorrelationId());
        response.setTags(List.of("MOCK_PLAYER"));

        return ResponseEntity.ok(response);
    }

    /**
     * POST /player-balance : Fetch player balance.
     *
     * @param request MockPlayer balance Request Object (required)
     * @return successful operation (status code 200)
     */
    @PostMapping(value = "/player-balance")
    public ResponseEntity<Wallet> playerBalance(
            @RequestBody PlayerBalanceRequest request,
            @RequestHeader(required = false, defaultValue = "default") String tenant) {
        
        log.info("{}", request);

        Player mockPlayer = mockPlayerRepository.findByCorrelationId(request.getToken());

        if (mockPlayer == null) {
            throw new BaseRuntimeException(MockPlayerConnectErrorCode.INVALID_TOKEN);
        }

        log.info("Found {} {} {}", mockPlayer.getCorrelationId(), mockPlayer.getBrand(), mockPlayer.getTenant());

        GameSession gameSession = new GameSession(null, null, null, null);
        gameSession.setBrand(mockPlayer.getBrand());
        gameSession.setToken(mockPlayer.getCorrelationId());
        gameSession.setCurrency(mockPlayer.getUid().split("\\|")[1]);
        gameSession.setTenant(mockPlayer.getTenant());
        gameSession.setPlayer(mockPlayer.getUid());
        
        log.info("mock balance {}", gameSession);
        
        Wallet playerWallet = mockPlayerServiceAdapter.playerBalance(gameSession);
        log.info("mockPlayer wallet {}", playerWallet);
        
        return ResponseEntity.ok(playerWallet);
    }

    /**
     * POST /player-transaction : MockPlayer game transaction
     *
     * @param request MockPlayer Transaction Request (required)
     * @return successful operation (status code 200)
     */
    @PostMapping(value = "/player-transaction")
    public ResponseEntity<PlayerTransactionResponse> playerTransaction(
            @RequestBody PlayerTransactionRequestV1 request,
            @RequestHeader(required = false, defaultValue = "default") String tenant) {
        
        String simulatedErrorCode = gameRoundMockErrors.remove(request.getPlayerId());
        log.info("{} mock error {}", request, simulatedErrorCode);
        
        if (simulatedErrorCode != null) {
            SystemErrorCode errorCode = null;
            try {
                errorCode = SystemErrorCode.valueOf(simulatedErrorCode);
            } catch (Exception e) {
                log.error("{}", e.getMessage());
            }
            throw new BaseRuntimeException(errorCode);
        }

        if (request.getPlayerId() != null && request.getPlayerId().startsWith("mock-player")) {
            double rand = (Math.random() * 100);
            if (request.getDebit() != null && request.getDebit().getAmount().compareTo(BigDecimal.ZERO) > 0 && (rand > 50)) {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST);
            } else if (request.getCredit() != null && request.getCredit().getAmount().compareTo(BigDecimal.ZERO) > 0 && (rand > 50)) {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST);
            }
        }

        // Delay simulation for mock players
        if (request.getPlayerId() != null && request.getPlayerId().startsWith("mock-player")) {
            if (request.getRequestType() != TransactionType.ROLLBACK) {
                log.info("Delaying request for 30 secs");
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        Player mockPlayer;
        if (request.getPlayerId() != null && request.getPlayerId().startsWith("auto-player")) {
            if (request.getPlayerId() == null) {
                throw new BaseRuntimeException(MockPlayerConnectErrorCode.PLAYER_ID_NOT_FOUND);
            }

            SetOperations<String, String> setOps = redisTemplate.opsForSet();
            Set<String> members = setOps.members("mock-player:uid:" + request.getPlayerId() + "|" + request.getCurrency());
            
            if (members == null || members.isEmpty()) {
                throw new BaseRuntimeException(MockPlayerConnectErrorCode.PLAYER_ID_NOT_FOUND);
            }
            
            String playerId = members.iterator().next();
            mockPlayer = findPlayerInRedis(playerId);
        } else {
            if (request.getToken() == null) {
                throw new BaseRuntimeException(MockPlayerConnectErrorCode.INVALID_TOKEN);
            }

            SetOperations<String, String> setOps = redisTemplate.opsForSet();
            Set<String> members = setOps.members("mock-player:correlationId:" + request.getToken());
            
            if (members == null || members.isEmpty()) {
                throw new BaseRuntimeException(MockPlayerConnectErrorCode.INVALID_TOKEN);
            }
            
            String playerId = members.iterator().next();
            mockPlayer = findPlayerInRedis(playerId);
        }

        if (mockPlayer == null) {
            throw new BaseRuntimeException(MockPlayerConnectErrorCode.INVALID_TOKEN);
        }

        log.info("Found mockPlayer {}", mockPlayer);
        
        GameSession gameSession = new GameSession(null, mockPlayer.getBrand(), mockPlayer.getUid(), request.getCurrency());
        gameSession.setToken(mockPlayer.getCorrelationId());
        gameSession.setCurrency(request.getCurrency());
        gameSession.setBrand(mockPlayer.getBrand());
        gameSession.setTenant(mockPlayer.getTenant());
        gameSession.setPlayer(mockPlayer.getUid());

        PlayerTransactionResponse response = mockPlayerServiceAdapter.playerTransaction(gameSession, request);
        
        if (response == null) {
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR);
        }

        log.info("{}", response);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/player/init-session")
    public ResponseEntity<ObjectNode> initSession(
            HttpServletRequest servletRequest,
            @RequestHeader(name = "tenant", defaultValue = "default") String tenant,
            @RequestParam(name = "brand", defaultValue = "default") String brand,
            @RequestParam() String gameId,
            @RequestParam() String playerId,
            @RequestParam(defaultValue = "USD") String currency,
            @RequestParam(defaultValue = "en_GB") String locale) {

        String remoteHost = servletRequest.getHeader("X-Forwarded-Host");
        String host = servletRequest.getHeader("Host");

        if (remoteHost == null && host != null) {
            remoteHost = "https://" + host;
        }

        if (remoteHost != null && remoteHost.contains("localhost")) {
            remoteHost = remoteHost.replace("https", "http");
        }

        log.info("remoteHost {}", remoteHost);
        log.info("brand {}", brand);
        
        Map<String, String> map = new HashMap<>();
        map.put("brand", brand);
        map.put("playerId", playerId + "|" + currency);
        map.put("currency", currency);
        map.put("gameId", gameId);
        map.put("locale", locale);
        
        log.info("{} headers {}", map, servletRequest.getHeaderNames());

        String url = remoteHost + basPath + "/games/init-session";

        try {
            ObjectNode initSessionResponse = RestClient.builder().build().post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("tenant", tenant)
                    .body(objectMapper.convertValue(map, JsonNode.class))
                    .retrieve()
                    .body(ObjectNode.class);
            
            log.info("response from rgs {}", initSessionResponse);
            
            String launchToken = initSessionResponse.get("launchToken").asText();

            Player player = mockPlayerRepository.findByUid(playerId + "|" + currency);
            if (player == null) {
                player = createPlayer(tenant, brand, playerId, currency);
            }

            player.setCorrelationId(launchToken);

            GameSession gameSession = new GameSession(tenant, brand, player.getUid(), currency);
            gameSession.setToken(launchToken);

            log.info("Player {} {}", player, gameSession.getPlayer());

            mockPlayerRepository.save(player);
            mockPlayerServiceAdapter.playerBalance(gameSession);

            return ResponseEntity.ok(initSessionResponse);
            
        } catch (Exception e) {
            log.error("Error calling RGS: {}", e.getMessage());
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }

    @PostMapping(value = "/player/mock/{gameRoundId}/{currency}/{errorCode}")
    public ResponseEntity<Void> mock(
            @PathVariable() String gameRoundId,
            @PathVariable() String currency,
            @PathVariable() String errorCode) {

        gameRoundMockErrors.put(gameRoundId + "|" + currency, errorCode);
        return ResponseEntity.ok().build();
    }

    private Player findPlayerInRedis(String playerId) {
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
        
        String uid = (String) hashOps.get("mock-player:" + playerId, "uid");
        String correlationId = (String) hashOps.get("mock-player:" + playerId, "correlationId");
        String brand = (String) hashOps.get("mock-player:" + playerId, "brand");
        String tenantValue = (String) hashOps.get("mock-player:" + playerId, "tenant");
        
        if (uid == null) {
            return null;
        }
        
        Player player = new Player();
        player.setUid(uid);
        player.setCorrelationId(correlationId);
        player.setBrand(brand);
        player.setTenant(tenantValue);
        
        return player;
    }

    public enum MockPlayerConnectErrorCode implements ErrorCode {
        INVALID_TOKEN("401", "Invalid Token", 401),
        PLAYER_ID_NOT_FOUND("304", "Player ID not found", 400);

        private final String code;
        private final String description;
        private final int httpStatusCode;

        MockPlayerConnectErrorCode(String code, String description, int httpStatusCode) {
            this.code = code;
            this.description = description;
            this.httpStatusCode = httpStatusCode;
        }

        @Override
        public String getCode() {
            return this.code;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public int getHttpStatusCode() {
            return httpStatusCode;
        }
    }
}
