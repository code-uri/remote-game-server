package in.aimlabs.gaming.operator.aggregator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.aimlabs.gaming.dto.*;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import in.aimlabs.gaming.services.*;
import aimlabs.gaming.rgs.core.exceptions.BaseException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import in.aimlabs.gaming.operator.aggregator.services.AggregatorPlayerServiceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.observability.DefaultSignalListener;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/connect")
@Data
@Slf4j
public class PlayerConnectController implements PlayerBalanceApi, PlayerInitialiseApi, PlayerTransactionApi {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    IGameSessionService gameSessionService;

    @Autowired
    IPlayerService playerService;

    @Autowired
    IBrandService brandService;

    @Autowired
    IGameSkinService gameSkinService;

    @Autowired
    ITransactionService transactionService;

    @Autowired
    IGameRoundService gameRoundService;

    @Autowired
    AggregatorPlayerServiceManager aggregatorPlayerServiceManager;

    /**
     * POST /player-initialise : Initiate player game session. Sends player wallet
     * balance and adhoc settings or regulations.
     *
     * @param requestMono Player Initialise Request (required)
     * @param exchange    {@link ServerWebExchange}
     * @return successful operation (status code 200)
     */

    @Override
    public Mono<ResponseEntity<PlayerInitialiseResponse>> playerInitialise(Mono<PlayerInitialiseRequest> requestMono,
            ServerWebExchange exchange, @RequestHeader(defaultValue = "default") String tenant) {

        return requestMono
                .tap(() -> new DefaultSignalListener<PlayerInitialiseRequest>() {
                    @Override
                    public void doOnNext(PlayerInitialiseRequest request) {
                        log.info("initialise api {}  headers {}", request, exchange.getRequest().getHeaders());
                    }
                })
                .flatMap(request -> {
                    request.setTenant(tenant);
                    return aggregatorPlayerServiceManager.playerInitialise(request);
                })
                .tap(() -> new DefaultSignalListener<>() {
                    @Override
                    public void doOnNext(PlayerInitialiseResponse processed) {
                        log.info("initialise api end {}", processed);
                    }
                }).map(ResponseEntity::ok)
                .contextWrite(context -> context.put("tenant", tenant))
                .onErrorResume(throwable -> {

                    if (throwable instanceof BaseException BaseException
                            && BaseException.getErrorCode() == SystemErrorCode.TOKEN_EXPIRED) {
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                    }

                    return Mono.error(throwable);
                }).switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()))
                // .doOnSuccess(playerInitialiseResponseResponseEntity ->
                // exchange.getResponse().getHeaders().set("traceparent", "123123"))
                .contextCapture();

    }

    @Override
    public Mono<ResponseEntity<PlayerTransactionResponse>> playerTransaction(
            @RequestBody Mono<PlayerTransactionRequest> playerTransactionRequest, ServerWebExchange exchange,
            @RequestHeader(defaultValue = "default") String tenant) {

        return playerTransactionRequest
                .tap(() -> new DefaultSignalListener<>() {
                    @Override
                    public void doOnNext(PlayerTransactionRequest request) {
                        log.info("transaction api {} headers {}", request, exchange.getRequest().getHeaders());
                    }
                })
                .flatMap(request -> {
                    request.setTenant(tenant);
                    return aggregatorPlayerServiceManager.playerTransaction(request);
                })
                .tap(() -> new DefaultSignalListener<>() {
                    @Override
                    public void doOnNext(PlayerTransactionResponse processed) {
                        log.info("transaction api end {}", processed);
                    }
                }).map(ResponseEntity::ok)
                .contextWrite(context -> context.put("tenant", tenant))
                .onErrorResume(throwable -> {

                    if (throwable instanceof BaseException BaseException
                            && BaseException.getErrorCode() == SystemErrorCode.TOKEN_EXPIRED) {
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                    }

                    return Mono.error(throwable);
                }).switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()))
                // .doOnSuccess(playerInitialiseResponseResponseEntity ->
                // exchange.getResponse().getHeaders().set("traceparent", "123123"))
                .contextCapture();
    }

    @Override
    public Mono<ResponseEntity<Wallet>> playerBalance(@RequestBody Mono<PlayerBalanceRequest> playerBalanceRequestMono,
            ServerWebExchange exchange, @RequestHeader(defaultValue = "default") String tenant) {
        return playerBalanceRequestMono
                .tap(() -> new DefaultSignalListener<>() {
                    @Override
                    public void doOnNext(PlayerBalanceRequest request) {
                        log.info("balance api {}  headers {}", request, exchange.getRequest().getHeaders());
                    }
                })
                .flatMap(request -> {
                    request.setTenant(tenant);
                    return aggregatorPlayerServiceManager.playerBalance(request);
                })
                .tap(() -> new DefaultSignalListener<>() {
                    @Override
                    public void doOnNext(Wallet processed) {
                        log.info("balance api end {}", processed);
                    }
                }).map(ResponseEntity::ok)
                .contextWrite(context -> context.put("tenant", tenant))
                .onErrorResume(throwable -> {

                    if (throwable instanceof BaseException BaseException
                            && BaseException.getErrorCode() == SystemErrorCode.TOKEN_EXPIRED) {
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                    }

                    return Mono.error(throwable);
                }).switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()))
                // .doOnSuccess(playerInitialiseResponseResponseEntity ->
                // exchange.getResponse().getHeaders().set("traceparent", "123123"))
                .contextCapture();

    }

    public Mono<ResponseEntity<PlayerTransactionResponse>> rollback(
            @RequestBody Mono<PlayerTransactionRequest> playerTransactionRequest,
            ServerWebExchange exchange,
            @RequestHeader(defaultValue = "default") String tenant) {

        return playerTransactionRequest
                .tap(() -> new DefaultSignalListener<>() {
                    @Override
                    public void doOnNext(PlayerTransactionRequest request) {
                        log.info("transaction api {} headers {}", request, exchange.getRequest().getHeaders());
                    }
                })
                .flatMap(request -> {
                    request.setTenant(tenant);
                    return aggregatorPlayerServiceManager.playerTransaction(request);
                })
                .tap(() -> new DefaultSignalListener<>() {
                    @Override
                    public void doOnNext(PlayerTransactionResponse processed) {
                        log.info("transaction api end {}", processed);
                    }
                }).map(ResponseEntity::ok)
                .contextWrite(context -> context.put("tenant", tenant))
                .onErrorResume(throwable -> {

                    if (throwable instanceof BaseException BaseException
                            && BaseException.getErrorCode() == SystemErrorCode.TOKEN_EXPIRED) {
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                    }

                    return Mono.error(throwable);
                }).switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()))
                // .doOnSuccess(playerInitialiseResponseResponseEntity ->
                // exchange.getResponse().getHeaders().set("traceparent", "123123"))
                .contextCapture();
    }
}
