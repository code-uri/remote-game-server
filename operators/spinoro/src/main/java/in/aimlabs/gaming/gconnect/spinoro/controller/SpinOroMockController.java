package in.aimlabs.gaming.gconnect.spinoro.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.aimlabs.gaming.dto.*;
import in.aimlabs.gaming.dto.GameSession;
import aimlabs.gaming.rgs.core.exceptions.BaseException;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.ErrorCode;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import in.aimlabs.gaming.gconnect.spinoro.service.SpinOroPlayerServiceAdaptor;
import in.aimlabs.gaming.mockpam.MockPlayerServiceAdapter;
import in.aimlabs.gaming.mockpam.MockPlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.observability.DefaultSignalListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/mock/spinoro")
@Data
@Slf4j
public class SpinOroMockController {

    @Value("${spring.webflux.base-path:/api/rgs}")
    private String basPath;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MockPlayerServiceAdapter mockPlayerServiceAdapter;

    @Autowired
    MockPlayerRepository mockPlayerRepository;

    @Autowired
    ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

    @Autowired
    WebClient.Builder webClientBuilder;
    @Autowired
    private HttpClient httpClient; // 1. Inject the shared HttpClient bean

    @Autowired
    ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    AtomicInteger readTimeOut = new AtomicInteger(3);
    private ConcurrentHashMap<String, String> gameRoundMockErrors = new ConcurrentHashMap<>();

    Mono<Player> createPlayer(String tenant, String brand, String playerId, String currency) {
        Player mockPlayer = new Player();
        mockPlayer.setTenant(tenant);
        mockPlayer.setBrand(brand);
        mockPlayer.setUid(playerId + "|" + currency);
        return Mono.just(mockPlayerRepository.save(mockPlayer))
                .tap(() -> new DefaultSignalListener<Player>() {

                    public void doOnNext(Player mockPlayer) throws Throwable {
                        log.info("Mock mockPlayer {} inserted", mockPlayer);
                    }
                });
    }

    @PostMapping(value = { "/debit", "/credit", "/rollbackDebit" })
    public Mono<SpinOroPlayerServiceAdaptor.TransactionResponse> request(
            @RequestBody Mono<SpinOroPlayerServiceAdaptor.TransactionRequest> transactionRequestMono,
            ServerWebExchange exchange,
            @RequestHeader(defaultValue = "default") String tenant) {

        return transactionRequestMono
                // .publishOn(Schedulers.boundedElastic())
                .flatMap(request -> {
                    // return Mono.just(new PlayerTransactionResponse());
                    Mono<String> mockPlayerMono;
                    if ((request.playerId() != null && request.playerId().startsWith("auto-player"))) {

                        mockPlayerMono = reactiveStringRedisTemplate.opsForSet()
                                .members("mock-player:uid:" + request.playerId() + "|" + request.currency())
                                .last();
                        /*
                         * ,
                         * "mock-player:currency:" + request.getCurrency()
                         */

                        /*
                         * mockPlayerMono = Mono.defer(() -> Mono.just(
                         * .switchIfEmpty(Mono.error(new
                         * BaseRuntimeException(MockPlayerConnectErrorCode.PLAYER_ID_NOT_FOUND))));
                         */
                    } else {
                        if (request.securityToken() == null)
                            return Mono.error(new BaseRuntimeException(MockPlayerConnectErrorCode.INVALID_TOKEN));

                        // mockPlayerMono = Mono.defer(() ->
                        // Mono.just(mockPlayerRepository.findByToken(request.getSessionToken())));

                        mockPlayerMono = reactiveStringRedisTemplate.opsForSet()
                                .members("mock-player:correlationId:" + request.securityToken())
                                .last();
                    }

                    return mockPlayerMono
                            .switchIfEmpty(
                                    Mono.error(new BaseRuntimeException(MockPlayerConnectErrorCode.INVALID_TOKEN)))
                            // .switchIfEmpty(Mono.error(new
                            // BaseRuntimeException(MockPlayerConnectErrorCode.PLAYER_ID_NOT_FOUND)))
                            .flatMap(o -> {
                                return Mono.zip(
                                        reactiveStringRedisTemplate.opsForHash().get("mock-player:" + o,
                                                "correlationId"),
                                        reactiveStringRedisTemplate.opsForHash().get("mock-player:" + o, "brand"),
                                        reactiveStringRedisTemplate.opsForHash().get("mock-player:" + o, "tenant"))
                                        .map(tuples3 -> {
                                            Player mp = new Player();
                                            mp.setCorrelationId(tuples3.getT1().toString());
                                            mp.setBrand(tuples3.getT2().toString());
                                            mp.setTenant(tuples3.getT3().toString());
                                            // mp.setCurrency(values.get(1).toString());
                                            return mp;
                                        });
                            })
                            .flatMap(mockPlayer -> {
                                log.info("Found mockPlayer {}", mockPlayer);
                                GameSession gameSession = new GameSession(null, mockPlayer.getBrand(),
                                        mockPlayer.getCorrelationId(), request.currency());
                                gameSession.setToken(mockPlayer.getCorrelationId());
                                gameSession.setCurrency(request.currency());
                                gameSession.setBrand(mockPlayer.getBrand());
                                gameSession.setTenant(mockPlayer.getTenant());
                                // PlayerTransactionRequest tx = createPlayerTransaction(gameSession, debit,
                                // credit);

                                PlayerTransactionRequestV1 transactionRequest = new PlayerTransactionRequestV1();
                                transactionRequest.setTenant(tenant);
                                transactionRequest.setPlayerId(request.playerId());
                                transactionRequest
                                        .setCredit(new PlayerGameTransaction(BigDecimal.valueOf(request.amount()),
                                                request.transactionId()));
                                transactionRequest.setCurrency(request.currency());
                                transactionRequest.setGameId(request.providerGameId());
                                transactionRequest.setGameRoundId(request.roundId());

                                if (exchange.getRequest().getPath().value().endsWith("/debit"))
                                    transactionRequest.setRequestType(TransactionType.DEBIT);
                                else if (exchange.getRequest().getPath().value().endsWith("/credit"))
                                    transactionRequest.setRequestType(TransactionType.CREDIT);
                                else if (exchange.getRequest().getPath().value().endsWith("/rollbackDebit"))
                                    transactionRequest.setRequestType(TransactionType.ROLLBACK);
                                else
                                    return Mono.error(new BaseRuntimeException(PAMErrorCode.GENERAL_API_ERROR,
                                            "invalid  transaction type " + transactionRequest.getRequestType()));

                                return mockPlayerServiceAdapter.playerTransaction(gameSession, transactionRequest)
                                        // .doOnError(throwable -> throwable.printStackTrace())
                                        .switchIfEmpty(
                                                Mono.error(new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR)))
                                        .map(playerTransactionResponse -> {
                                            log.info("{}", playerTransactionResponse);
                                            return new SpinOroPlayerServiceAdaptor.TransactionResponse(true,
                                                    playerTransactionResponse.getTxnId(),
                                                    playerTransactionResponse.getWallet().getTotalBalance()
                                                            .multiply(BigDecimal.valueOf(100)).longValue(),
                                                    0L, 0L,
                                                    playerTransactionResponse.getWallet().getCurrency(), null);
                                        });
                            });
                    // .publishOn(Schedulers.boundedElastic());
                })

                .contextWrite(context -> context.put("TENANT", tenant));
    }

    @PostMapping(value = { "/debitAndCredit" })
    public Mono<SpinOroPlayerServiceAdaptor.TransactionResponse> debitAndCredit(
            @RequestBody Mono<SpinOroPlayerServiceAdaptor.DebitCreditRequest> debitCreditRequestMono,
            ServerWebExchange exchange,
            @RequestHeader(defaultValue = "default") String tenant) {

        return debitCreditRequestMono
                // .publishOn(Schedulers.boundedElastic())
                .flatMap(request -> {

                    return Flux.fromIterable(request.transactions())
                            .flatMap(transaction -> {
                                // return Mono.just(new PlayerTransactionResponse());
                                Mono<String> mockPlayerMono;
                                if ((request.playerId() != null && request.playerId().startsWith("auto-player"))) {

                                    mockPlayerMono = reactiveStringRedisTemplate.opsForSet()
                                            .members("mock-player:uid:" + request.playerId() + "|"
                                                    + transaction.currency())
                                            .last();
                                    /*
                                     * ,
                                     * "mock-player:currency:" + request.getCurrency()
                                     */

                                    /*
                                     * mockPlayerMono = Mono.defer(() -> Mono.just(
                                     * .switchIfEmpty(Mono.error(new
                                     * BaseRuntimeException(MockPlayerConnectErrorCode.PLAYER_ID_NOT_FOUND))));
                                     */
                                } else {
                                    if (request.securityToken() == null)
                                        return Mono.error(
                                                new BaseRuntimeException(MockPlayerConnectErrorCode.INVALID_TOKEN));

                                    // mockPlayerMono = Mono.defer(() ->
                                    // Mono.just(mockPlayerRepository.findByToken(request.getSessionToken())));

                                    mockPlayerMono = reactiveStringRedisTemplate.opsForSet()
                                            .members("mock-player:correlationId:" + request.securityToken())
                                            .last();
                                }

                                return mockPlayerMono
                                        .switchIfEmpty(Mono.error(
                                                new BaseRuntimeException(MockPlayerConnectErrorCode.INVALID_TOKEN)))
                                        // .switchIfEmpty(Mono.error(new
                                        // BaseRuntimeException(MockPlayerConnectErrorCode.PLAYER_ID_NOT_FOUND)))
                                        .flatMap(o -> {
                                            return Mono
                                                    .zip(reactiveStringRedisTemplate.opsForHash()
                                                            .get("mock-player:" + o, "correlationId"),
                                                            reactiveStringRedisTemplate.opsForHash()
                                                                    .get("mock-player:" + o, "brand"),
                                                            reactiveStringRedisTemplate.opsForHash()
                                                                    .get("mock-player:" + o, "tenant"))
                                                    .map(tuples3 -> {
                                                        Player mp = new Player();
                                                        mp.setCorrelationId(tuples3.getT1().toString());
                                                        mp.setBrand(tuples3.getT2().toString());
                                                        mp.setTenant(tuples3.getT3().toString());
                                                        // mp.setCurrency(values.get(1).toString());
                                                        return mp;
                                                    });
                                        })
                                        .flatMap(mockPlayer -> {
                                            log.info("Found mockPlayer {}", mockPlayer);
                                            GameSession gameSession = new GameSession(null, mockPlayer.getBrand(),
                                                    mockPlayer.getCorrelationId(), transaction.currency());
                                            gameSession.setToken(mockPlayer.getCorrelationId());
                                            gameSession.setCurrency(transaction.currency());
                                            gameSession.setBrand(mockPlayer.getBrand());
                                            gameSession.setTenant(mockPlayer.getTenant());
                                            // PlayerTransactionRequest tx = createPlayerTransaction(gameSession, debit,
                                            // credit);

                                            PlayerTransactionRequestV1 transactionRequest = new PlayerTransactionRequestV1();
                                            transactionRequest.setTenant(tenant);
                                            transactionRequest.setPlayerId(request.playerId());
                                            transactionRequest.setCredit(
                                                    new PlayerGameTransaction(BigDecimal.valueOf(transaction.amount()),
                                                            transaction.transactionId()));
                                            transactionRequest.setCurrency(transaction.currency());
                                            transactionRequest.setGameId(request.providerGameId());
                                            transactionRequest.setGameRoundId(request.roundId());

                                            if ("DEBIT".equals(transaction.type()))
                                                transactionRequest.setRequestType(TransactionType.DEBIT);
                                            else
                                                transactionRequest.setRequestType(TransactionType.CREDIT);

                                            return mockPlayerServiceAdapter
                                                    .playerTransaction(gameSession, transactionRequest)
                                                    // .doOnError(throwable -> throwable.printStackTrace())
                                                    .switchIfEmpty(Mono.error(
                                                            new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR)))
                                                    .map(playerTransactionResponse -> {
                                                        log.info("{}", playerTransactionResponse);
                                                        return new SpinOroPlayerServiceAdaptor.TransactionResponse(true,
                                                                playerTransactionResponse.getTxnId(),
                                                                playerTransactionResponse.getWallet().getTotalBalance()
                                                                        .multiply(BigDecimal.valueOf(100)).longValue(),
                                                                0L, 0L,
                                                                playerTransactionResponse.getWallet().getCurrency(),
                                                                null);
                                                    });
                                        });
                                // .publishOn(Schedulers.boundedElastic());
                            }).last();
                })

                .contextWrite(context -> context.put("TENANT", tenant));

    }

    @PostMapping(value = { "/initGame", "/balance" })
    public Mono<SpinOroPlayerServiceAdaptor.InitGameResponse> getBalance(
            @RequestBody SpinOroPlayerServiceAdaptor.InitGameRequest initGameRequest,
            ServerWebExchange exchange,
            @RequestHeader(required = false, defaultValue = "default") String tenant) {

        log.info("{}", initGameRequest);
        return Mono.defer(() -> {
            Player mockPlayer = mockPlayerRepository.findByCorrelationId(initGameRequest.securityToken());
            if (mockPlayer == null)
                return Mono.empty();

            log.info("Found {} {} {}", mockPlayer.getCorrelationId(), mockPlayer.getBrand(), mockPlayer.getTenant());
            return Mono.just(mockPlayer);
        })
                .switchIfEmpty(Mono.error(new BaseException(MockPlayerConnectErrorCode.INVALID_TOKEN)))
                .flatMap(mockPlayer -> {
                    GameSession gameSession = new GameSession();
                    gameSession.setBrand(mockPlayer.getBrand());
                    gameSession.setToken(mockPlayer.getCorrelationId());
                    gameSession.setCurrency(mockPlayer.getUid().split("\\|")[1]);
                    gameSession.setTenant(mockPlayer.getTenant());
                    log.info("mock balance {}", gameSession);
                    return mockPlayerServiceAdapter.playerBalance(gameSession)
                            .doOnNext(wallet -> log.info("mockPlayer wallet {}", wallet))
                            .map(playerWallet -> {
                                return new SpinOroPlayerServiceAdaptor.InitGameResponse(true,
                                        Double.valueOf(playerWallet.getTotalBalance().doubleValue() * 100).longValue(),
                                        0, 0, null);
                            });
                })
                .contextWrite(context -> context.put("TENANT", tenant));

    }

    @PostMapping(value = "/player/init-session")
    Mono<ResponseEntity<Object>> createPlayer(ServerWebExchange serverWebExchange,
            @RequestHeader(name = "tenant", defaultValue = "default") String tenant,
            @RequestParam(name = "brand", defaultValue = "default") String brand,
            @RequestParam() String gameId,
            @RequestParam() String launchToken,
            @RequestParam() String playerId,
            @RequestParam(defaultValue = "USD") String currency,
            @RequestParam(defaultValue = "en_GB") String locale) {

        return createPlayer(tenant, brand, playerId, currency)
                .flatMap(mockPlayer -> {
                    mockPlayer.setCorrelationId(launchToken);
                    GameSession gameSession = new GameSession(tenant, brand, playerId, currency);
                    gameSession.setToken(mockPlayer.getCorrelationId());

                    return Mono.just(mockPlayerRepository.save(mockPlayer))
                            .flatMap(player -> {
                                return mockPlayerServiceAdapter.playerBalance(gameSession);
                            });
                }).then()
                .map(as -> ResponseEntity.ok().build())
                .contextWrite(context -> context.put("TENANT", tenant));
    }

    @PostMapping(value = "/player/mock/{gameRoundId}/{errorCode}")
    Mono<ResponseEntity> mock(@PathVariable() String gameRoundId,
            @PathVariable() String errorCode) {

        gameRoundMockErrors.put(gameRoundId, errorCode);
        return Mono.just(ResponseEntity.ok().build());
    }

    public enum MockPlayerConnectErrorCode implements ErrorCode {

        // gameactivity
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

        public String getCode() {
            return this.code;
        }

        public String getDescription() {
            return description;
        }

        public int getHttpStatusCode() {
            return httpStatusCode;
        }

        public enum PLAYER_ID_NOT_FOUND {
        }
    }
}
