package in.aimlabs.gaming.gconnect.astroplay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.aimlabs.gaming.api.connect.PlayerAccountManager;
import in.aimlabs.gaming.api.connect.PlayerAccountManagerFactory;
import in.aimlabs.gaming.api.connect.utils.PAMErrorsUtils;
import in.aimlabs.gaming.connectors.api.model.Connector;
import in.aimlabs.gaming.connectors.api.services.AbstractConnectorWebClientBuilderService;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;

import in.aimlabs.gaming.gconnect.astroplay.controller.AstroPlayConnectController;
import in.aimlabs.gaming.gconnect.astroplay.dto.*;
import in.aimlabs.genesis.core.exceptions.BaseRuntimeException;
import in.aimlabs.genesis.core.exceptions.SystemErrorCode;
import in.aimlabs.genesis.core.utils.TapOnNextSignalListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.observability.DefaultSignalListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Retry;
import reactor.util.function.Tuple2;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static reactor.util.retry.Retry.withThrowable;

@Component
@Qualifier("astroplay")
@Slf4j
@Getter
public class AstroPlayPlayerServiceAdaptor
        implements PlayerAccountManagerFactory, AstroPlayService {

    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    public static final String TRANSACTION_SUCCESS = "success";

    @Value("${rgs.player.connector.marvel.uid:astro-connector}")
    String connectorUid;

    @Value("${rgs.player.connector.retries:3}")
    private String transactionRetries;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    WebClient.Builder webClientBuilder;
    @Autowired
    private HttpClient httpClient; // 1. Inject the shared HttpClient bean

    public static final String SALT = "reevotest";

    public static final String CALLERID = "reevotest";

    public boolean supports(Connector connector) {
        return connectorUid.equals(connector.getUid())
                || connectorUid.equals(connector.getParentConnector());
    }

    @Override
    public PlayerAccountManager getInstance(Connector connector) {
        return new MarvelPlayerServiceConnector(connector);
    }

    @Override
    public Mono<IntegrationMinimalDto> getIntegration(String providerId, long operatorId) {
        return null;
    }

    @Override
    public Mono<BalanceResponse> init(IntegrationsBaseBalanceRequest integrationsBaseBalanceRequest) {
        return null;
    }

    @Override
    public Mono<BalanceResponse> getPlayerBalance(IntegrationsBaseBalanceRequest integrationsBaseBalanceRequest) {
        return null;
    }

    @Override
    public Mono<SessionState> checkSession(CheckGameSessionRequest request) {
        // Log the incoming request
        log.info("Checking game session for PlayerId: {} with ExtSessionToken: {}", request.getPlayerId(),
                request.getExtSessionToken());

        // Example logic to check the game session
        // This could involve calling an external service or checking a database
        return Mono.fromCallable(() -> {
            // Simulate a session check
            boolean sessionValid = validateSession(request).block();
            return sessionValid;
        }).doOnSuccess(valid -> {
            if (valid) {
                log.info("Session is valid for PlayerId: {}", request.PlayerId());
            } else {
                log.warn("Session is invalid for PlayerId: {}", request.PlayerId());
            }
        }).onErrorResume(e -> {
            log.error("Error checking game session for PlayerId: {}", request.PlayerId(), e);
            return Mono.just(false);
        });
    }

    @Override
    public Mono<BalanceResponse> processPlay(ProcessBetWinRequest processBetWinRevokeRequest) {
        return null;
    }

    @Override
    public Mono<BalanceResponse> processRevoke(ProcessRevokeRequest processRevokeRequest) {
        return null;
    }

    @Override
    public Mono<BalanceResponse> processPartialRefund(ProcessRevokeRequest processPartialRefundRequest) {
        return null;
    }

    @Override
    public Mono<UpdateSessionDataResponse> updateGameSession(UpdateSessionDataRequest updateSessionDataRequest) {
        return null;
    }

    static record GetBalanceRes(String type, String username, String status, String currency, String balance) {
    }

    static record GetBalanceReq(String type, String status, String token) {
    }

    static record BetRequest(String type, String transactionId, String token, String roundId, String tableId,
            String username, String currency, String amount) {
    }

    static record PlayerBetRequest(String type, String transactionId, String token, String round, String tableId,
            String currency, double amount, String userName, String reverseTransactionId) {
    }

    static record PlayerBetResponse(String type, String transactionId, String token, String status, String currency,
            double balance, String userName) {
    }

    class MarvelPlayerServiceConnector extends AbstractConnectorWebClientBuilderService
            implements PlayerAccountManager {

        MarvelPlayerServiceConnector(Connector connector) {
            super(webClientBuilder, connector);
        }

        public Mono<PlayerInitialiseResponse> playerInitialise(PlayerInitialiseRequest request) {

            long startMillis = System.currentTimeMillis();

            GetBalanceReq req = new GetBalanceReq("balance", TRANSACTION_SUCCESS, request.getSessionToken());

            ///api/seamless/s2x/qg?
            return getWebClient()
                    .post()
                    .body(req, GetBalanceReq.class)
                    // .uri("?" + queryString + "&key=" + key)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(String.class)
                    // .publishOn(Schedulers.parallel())
                    .tap(() -> new TapOnNextSignalListener<String>() {

                        public void doOnNext(String json) throws Throwable {
                            log.info("Marvel Balance response json. {}", json);
                        }
                    })
                    .map(s -> {
                        try {
                            return objectMapper.readValue(s, GetBalanceRes.class);
                        } catch (JsonProcessingException e) {
                            log.info("Marvel Balance response json parse failed. {}", s, e);
                        }
                        throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR);
                    })
                    .flatMap(balanceResponse -> {
                        if ("failure".equals(balanceResponse.status)) {
                            // if (SESSION_NOT_FOUND.equals(balanceResponse.getCode()))
                            // return Mono.error(new BaseException(SystemErrorCode.TOKEN_EXPIRED));
                            //
                            // else if (INSUFFICIENT_BALANCE.name().equals(balanceResponse.getCode()))
                            // return Mono.error(new BaseException(INSUFFICIENT_BALANCE));
                            // else if
                            // (SystemErrorCode.SYSTEM_ERROR.name().equals(balanceResponse.getCode()))
                            // return Mono.error(new BaseException(SystemErrorCode.SYSTEM_ERROR));

                            return Mono.error(new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR,
                                    "Get Balance Failed!"));
                        }

                        return Mono.just(balanceResponse);
                    })
                    .retryWhen(withThrowable(Retry.anyOf(RuntimeException.class)
                            .exponentialBackoffWithJitter(Duration.ofSeconds(1), Duration.ofSeconds(5))
                            .retryMax(Long.parseLong(transactionRetries))))
                    .tap(() -> new DefaultSignalListener<GetBalanceRes>() {
                        public void doOnNext(GetBalanceRes getBalanceRes) throws Throwable {
                            log.info("Marvel Balance response. {}", getBalanceRes);
                        }

                        public void doFinally(SignalType terminationType) throws Throwable {
                            log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
                        }

                        public void doOnError(Throwable error) throws Throwable {
                            log.error("Marvel playerInfo request failed.", error);
                        }
                    })
                    .map(balanceRes -> {
                        PlayerInitialiseResponse res;
                        try {
                            res = new PlayerInitialiseResponse();
                            res.setCurrency(request.getCurrency());
                            res.setPlayerId(request.getPlayer());

                            BigDecimal balanceAmt = new BigDecimal(balanceRes.balance);

                            Money balance = Money.of(balanceAmt.doubleValue(),
                                    Monetary.getCurrency(balanceRes.currency));
                            res.setTotalBalance(balance.getNumberStripped());
                            res.setCash(new Balance().amount(balance.getNumberStripped()).onHold(BigDecimal.ZERO)
                                    .total(balance.getNumberStripped()));
                            res.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO)
                                    .total(BigDecimal.ZERO));
                            res.setExternalToken(request.getSessionToken());
                        } catch (Exception e) {
                            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
                        }
                        return res;
                    })
                    .tap(() -> new DefaultSignalListener<PlayerInitialiseResponse>() {

                        public void doOnError(Throwable error) throws Throwable {
                            log.info("Error creating player initialise response object.", error);
                        }
                    });
        }

        public Mono<Wallet> playerBalance(PlayerBalanceRequest request) {


            return webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/handler/integration")
                            .queryParam("hash", )
                            .build())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(e -> {
                        // Handle error logging here
                        System.err.println("Error occurred: " + e.getMessage());
                    });
        }

        private Mono<Boolean> validateSession(CheckGameSessionRequest request) {
            return webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/handler/integration")
                            .queryParam("hash", "")
                            .build())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(e -> {
                        // Handle error logging here
                        System.err.println("Error occurred: " + e.getMessage());
                    });
        }

        public Mono<PlayerTransactionResponse> playerTransaction(PlayerTransactionRequest request) {
            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            PlayerGameTransaction zero = new PlayerGameTransaction();
            zero.setAmount(BigDecimal.ZERO);

            TransactionType type = request.getRequestType();
            List<Mono<Tuple2<PlayerBetResponse, String>>> txsFlux = new ArrayList<>();
            if (type == TransactionType.ROLLBACK) {
                txsFlux.add(processTxn(request.getToken(),
                        request.getTxnId(), null,
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        0D,
                        TransactionType.ROLLBACK, request.getGameRoundId(),
                        true));
            } else {
                if (type == TransactionType.CREDIT) {
                    if (request.getCredit() == null) {
                        log.info("No wins for gameRound {}. sending zero wins request ", request.getGameRoundId());
                        txsFlux.add(processTxn(request.getToken(),
                                request.getTxnId(), null,
                                request.getGameId(),
                                request.getPlayerId(),
                                cu,
                                0D,
                                request.getRequestType(), request.getGameRoundId(),
                                request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                    } else {
                        txsFlux.add(processTxn(request.getToken(),
                                request.getTxnId(), null,
                                request.getGameId(),
                                request.getPlayerId(),
                                cu,
                                request.getCredit(),
                                request.getRequestType(),
                                request.getGameRoundId(),
                                request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                    }
                }
                if (type == TransactionType.DEBIT && request.getDebit() != null) {

                    Mono<Tuple2<PlayerBetResponse, String>> debitMono = processTxn(request.getToken(),
                            request.getTxnId(), null,
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            request.getDebit(),
                            request.getRequestType(), request.getGameRoundId(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED);
                    /*
                     * .onErrorResume(throwable -> {
                     * if(throwable instanceof BaseRuntimeException err){
                     * if(err.getErrorCode() == SystemErrorCode.ROLLBACK_GAME_ROUND){
                     * txReq.setRequestType(TransactionType.ROLLBACK);
                     * return processTxn(gameSession.getToken(), webClient,
                     * UUID.randomUUID().toString(),
                     * txReq.getGameId(),
                     * txReq.getPlayerId(),
                     * cu,
                     * txReq.getDebit(),
                     * TransactionType.ROLLBACK,
                     * txReq.getGameRoundId(), true)
                     * .then(Mono.error(new
                     * BaseRuntimeException(SystemErrorCode.GAME_ROUND_CANCELLED)));
                     * }
                     * }
                     * return Mono.error(throwable);
                     * });
                     */
                    txsFlux.add(debitMono);
                } else if (type == TransactionType.DEBIT_CREDIT) {
                    if (request.getDebit() == null) {
                        throw new BaseRuntimeException(PAMErrorCode.INVALID_REQUEST, "Debit amount is null");
                    } else if (request.getCredit() == null) {
                        throw new BaseRuntimeException(PAMErrorCode.INVALID_REQUEST, "Credit amount is null");
                    }

                    Mono<Tuple2<PlayerBetResponse, String>> debitMono = processTxn(request.getToken(),
                            request.getTxnId(), null,
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            request.getDebit(),
                            TransactionType.DEBIT, request.getGameRoundId(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED);
                    /*
                     * .onErrorResume(throwable -> {
                     * if(throwable instanceof BaseRuntimeException err){
                     * if(err.getErrorCode() == SystemErrorCode.ROLLBACK_GAME_ROUND){
                     * txReq.setRequestType(TransactionType.ROLLBACK);
                     * return processTxn(gameSession.getToken(), webClient,
                     * UUID.randomUUID().toString(),
                     * txReq.getGameId(),
                     * txReq.getPlayerId(),
                     * cu,
                     * txReq.getDebit(),
                     * TransactionType.ROLLBACK,
                     * txReq.getGameRoundId(), true)
                     * .then(Mono.error(new
                     * BaseRuntimeException(SystemErrorCode.GAME_ROUND_CANCELLED)));
                     * }
                     * }
                     * return Mono.error(throwable);
                     * });
                     */
                    txsFlux.add(debitMono);

                    txsFlux.add(processTxn(request.getToken(),
                            request.getTxnId(), null,
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            request.getCredit(),
                            TransactionType.CREDIT,
                            request.getGameRoundId(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));

                }

            }
            return Flux.concat(txsFlux)
                    .collectList()
                    .map(tuple2s -> {
                        Wallet wallet = new Wallet();
                        wallet.setCurrency(cu.getCurrencyCode());
                        Map<String, Object> processedTxIds = new HashMap<>();
                        tuple2s.forEach(tuple2 -> {
                            // log.info("tuple2 {}", tuple2);
                            PlayerBetResponse res = tuple2.getT1();
                            String orgTxId = tuple2.getT2();

                            if (TRANSACTION_SUCCESS.equals(res.status)) {
                                wallet.setTotalBalance(new BigDecimal(res.balance));
                                processedTxIds.put(orgTxId, "Operator not sending");
                                if (request.getRequestType() == TransactionType.ROLLBACK) {
                                    log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}",
                                            request.getGameId(), request.getTxnId());
                                    // response.setRollbackTxnId(res.getTransactions());
                                }
                            }
                        });

                        wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                                .total(wallet.getTotalBalance()));
                        wallet.setBonus(
                                new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));

                        response.setWallet(wallet);
                        response.setProcessedTxnIds(processedTxIds);
                        return response;
                    });

        }

        public Mono<PlayerTransactionResponse> rollback(PlayerTransactionRequest request) {

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            return processTxn(request.getToken(),
                    request.getTxnId(), request.getOrgTxnUid(),
                    request.getGameId(),
                    request.getPlayerId(),
                    cu,
                    null,
                    TransactionType.ROLLBACK,
                    request.getGameRoundId(),
                    true)
                    .map(tuple2 -> {
                        Map<String, Object> processedTxIds = new HashMap<>();

                        // log.info("tuple2 {}", tuple2);
                        PlayerBetResponse res = tuple2.getT1();
                        String orgTxId = tuple2.getT2();

                        if (TRANSACTION_SUCCESS.equals(res.status)) {
                            processedTxIds.put(orgTxId, "Operator not sending");
                            log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}", request.getGameId(),
                                    request.getTxnId());
                            // response.setRollbackTxnId(res.getProcessedTxId());
                            Wallet wallet = new Wallet();
                            wallet.setCurrency(cu.getCurrencyCode());

                            wallet.setTotalBalance(new BigDecimal(res.balance));
                            wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                                    .total(wallet.getTotalBalance()));
                            wallet.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO)
                                    .total(BigDecimal.ZERO));

                            response.setWallet(wallet);
                            response.setProcessedTxnIds(processedTxIds);
                        } else {
                            throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR);
                        }

                        return response;
                    });
        }

        private Mono<Tuple2<PlayerBetResponse, String>> processTxn(String sessionToken,
                String txnId, String originalTxnId,
                String gameId,
                String player,
                CurrencyUnit cu,
                Double amount,
                TransactionType type,
                String gameRoundId,
                boolean roundClosed) {

            log.info("Reevo player service. process transaction {} amount {}", type, amount);

            String reqType = null;
            if (type == TransactionType.DEBIT) {
                reqType = "bet";
            } else if (type == TransactionType.CREDIT) {
                reqType = "betResult";
            } else if (type == TransactionType.ROLLBACK) {
                reqType = "rollback";
            } else
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported transaction " + type);

            PlayerBetRequest betRequest = new PlayerBetRequest(reqType, txnId, sessionToken, gameRoundId, gameId,
                    cu.getCurrencyCode(), amount, player, originalTxnId);

            log.info("Reevo player service. process transaction request {}", betRequest);
            long startMillis = System.currentTimeMillis();

            return getWebClient()
                    .post()
                    .body(betRequest, PlayerBetRequest.class)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(PlayerBetResponse.class)
                    // .publishOn(Schedulers.parallel())
                    .flatMap(betResponse -> {
                        if (type == TransactionType.DEBIT && "failure".equals(betResponse.status)) {
                            // if (INSUFFICIENT_BALANCE.name().equals(balanceResponse.getCode()))
                            // return Mono.error(new BaseException(INSUFFICIENT_BALANCE));
                            // else if
                            // (SystemErrorCode.SYSTEM_ERROR.name().equals(balanceResponse.getCode()))
                            // return Mono.error(new BaseException(SystemErrorCode.SYSTEM_ERROR));

                            return Mono.error(new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND));
                        }
                        return Mono.just(betResponse);
                    })
                    .tap(() -> new DefaultSignalListener<PlayerBetResponse>() {
                        @Override
                        public void doOnNext(PlayerBetResponse response) {
                            log.info("{}", response);
                        }
                    })
                    /*
                     * .retryWhen(withThrowable(reactor.retry.Retry.anyOf(type ==
                     * TransactionType.CREDIT
                     * ? Throwable.class
                     * : RuntimeException.class)
                     * .exponentialBackoffWithJitter(Duration.ofSeconds(1), Duration.ofSeconds(5))
                     * .retryMax(Long.parseLong(transactionRetries))))
                     */
                    .zipWith(Mono.just(txnId))
                    .doOnError(throwable -> {
                        log.error("Marvel transaction {} failed.", txnId, throwable);
                    })
                    .doFinally(signalType -> {
                        if (signalType == SignalType.CANCEL)
                            log.info("Mono signalType {}. Transaction {} elapsed Time: {}ms", signalType, txnId,
                                    System.currentTimeMillis() - startMillis);
                    })
                    .doOnNext(response -> {
                        log.info("{}. Elapsed Time: {}ms", response, System.currentTimeMillis() - startMillis);
                    });
        }
    }
}
