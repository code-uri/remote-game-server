package in.aimlabs.gaming.gconnect.slotegrator.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.aimlabs.gaming.AbstractConnectorWebClientBuilderService;
import in.aimlabs.gaming.services.PlayerAccountManager;
import in.aimlabs.gaming.services.PlayerAccountManagerFactory;
import in.aimlabs.gaming.dto.*;
import in.aimlabs.gaming.utils.PAMErrorsUtils;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.core.exceptions.BaseException;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.core.utils.TapOnNextSignalListener;
import in.aimlabs.gaming.gconnect.slotegrator.client.BodyProvidingJsonEncoder;
import in.aimlabs.gaming.gconnect.slotegrator.client.MessageSigningHttpConnector;
import in.aimlabs.gaming.gconnect.slotegrator.client.Signer;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.observability.DefaultSignalListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.util.function.Tuple2;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static in.aimlabs.gaming.dto.PAMErrorCode.INSUFFICIENT_BALANCE;
import static reactor.util.retry.Retry.withThrowable;

@Component
@Qualifier("slotegrator")
@Slf4j
@Getter
public class SlotegratorPlayerServiceAdaptor
        implements PlayerAccountManagerFactory {

    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    @Value("${athena.player.connector.slotegrator.uid:slotegrator-connector}")
    String connectorUid;

    @Value("${athena.player.connector.retries:3}")
    private String transactionRetries;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    WebClient.Builder webClientBuilder;
    @Autowired
    private HttpClient httpClient; // 1. Inject the shared HttpClient bean

    public boolean supports(Connector connector) {
        return connectorUid.equals(connector.getUid())
                || connectorUid.equals(connector.getParentConnector());
    }

    @Override
    public PlayerAccountManager getInstance(Connector connector) {
        return new PlayerServiceConnector(connector);
    }

    @Getter
    @Setter
    @ToString
    static class GetBalanceResponse {
        boolean status;
        BigDecimal balance;
        String code;
        String message;
    }

    @Getter
    @Setter
    @ToString
    static class PlayRequest {

        String action;
        String type;
        String session_id;
        String amount;
        String transaction_id;
        String round_id;
        String game_id;

    }

    class PlayerServiceConnector extends AbstractConnectorWebClientBuilderService implements PlayerAccountManager {

        PlayerServiceConnector(Connector connector) {
            super(webClientBuilder, connector, httpClient);
        }

        public Mono<PlayerInitialiseResponse> playerInitialise(PlayerInitialiseRequest request) {

            long startMillis = System.currentTimeMillis();
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("action", "balance");
            requestMap.put("session_id", request.getSessionToken());

            return getWebClient()
                    .post()
                    .uri("/mplay")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestMap)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(String.class)
                    // .publishOn(Schedulers.parallel())
                    .tap(() -> new TapOnNextSignalListener<String>() {

                        public void doOnNext(String json) throws Throwable {
                            log.info("Slotegrator Balance response json. {}", json);
                        }
                    })
                    .map(s -> {
                        try {
                            return objectMapper.readValue(s, GetBalanceResponse.class);
                        } catch (JsonProcessingException e) {
                            log.info("Slotegrator Balance response json parse failed. {}", s, e);
                        }
                        throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR);
                    })
                    .flatMap(balanceResponse -> {
                        if (!balanceResponse.isStatus()) {

                            if (SESSION_NOT_FOUND.equals(balanceResponse.getCode()))
                                return Mono.error(new BaseException(SystemErrorCode.TOKEN_EXPIRED));

                            else if (INSUFFICIENT_BALANCE.name().equals(balanceResponse.getCode()))
                                return Mono.error(new BaseException(INSUFFICIENT_BALANCE));
                            else if (SystemErrorCode.SYSTEM_ERROR.name().equals(balanceResponse.getCode()))
                                return Mono.error(new BaseException(SystemErrorCode.SYSTEM_ERROR));

                            return Mono.error(new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR,
                                    balanceResponse.getMessage()));
                        }

                        return Mono.just(balanceResponse);
                    })
                    .retryWhen(withThrowable(reactor.retry.Retry.anyOf(RuntimeException.class)
                            .exponentialBackoffWithJitter(Duration.ofSeconds(1), Duration.ofSeconds(5))
                            .retryMax(Long.parseLong(transactionRetries))))
                    .tap(() -> new DefaultSignalListener<GetBalanceResponse>() {

                        public void doFinally(SignalType terminationType) throws Throwable {
                            log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
                        }

                        public void doOnError(Throwable error) throws Throwable {
                            log.error("Slotegrator playerInfo request failed.", error);
                        }
                    })
                    .tap(() -> new TapOnNextSignalListener<GetBalanceResponse>() {

                        public void doOnNext(GetBalanceResponse getBalanceResponse) throws Throwable {
                            getBalanceResponse.setCode(request.getCurrency());
                            log.info("Slotegrator Balance response. {}", getBalanceResponse);
                        }
                    })
                    .map(playerInfo -> {
                        PlayerInitialiseResponse res;
                        try {
                            res = new PlayerInitialiseResponse();
                            res.setCurrency(request.getCurrency());
                            res.setPlayerId(request.getPlayer());
                            Money balance = Money.of(playerInfo.getBalance(),
                                    Monetary.getCurrency(request.getCurrency()));
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
            PlayerInitialiseRequest playerInitialiseRequest = new PlayerInitialiseRequest();
            playerInitialiseRequest.setCurrency(request.getCurrency());
            playerInitialiseRequest.setPlayer(request.getPlayer());
            playerInitialiseRequest.setGameId(request.getGameId());
            playerInitialiseRequest.setSessionToken(request.getToken());
            return playerInitialise(playerInitialiseRequest).map(PlayerInitialiseResponse::getWallet);
        }

        public Mono<PlayerTransactionResponse> playerTransaction(PlayerTransactionRequest request) {
            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            PlayerGameTransaction zero = new PlayerGameTransaction();
            zero.setAmount(BigDecimal.ZERO);

            TransactionType type = request.getRequestType();
            List<Mono<Tuple2<GetBalanceResponse, String>>> txsFlux = new ArrayList<>();
            if (type == TransactionType.ROLLBACK) {
                txsFlux.add(processTxn(request.getToken(),
                        request.getTxnId(),
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
                                request.getTxnId(),
                                request.getGameId(),
                                request.getPlayerId(),
                                cu,
                                0D,
                                request.getRequestType(), request.getGameRoundId(),
                                request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                    } else {
                        txsFlux.add(processTxn(request.getToken(),
                                request.getTxnId(),
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
                    if (request.getDebit() == null) {
                        request.setDebit(0D);
                    }

                    Mono<Tuple2<GetBalanceResponse, String>> debitMono = processTxn(request.getToken(),
                            request.getTxnId(),
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
                            GetBalanceResponse res = tuple2.getT1();
                            String orgTxId = tuple2.getT2();
                            wallet.setTotalBalance(res.getBalance());
                            if (!res.isStatus()) {
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
                    request.getOrgTxnUid(),
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
                        GetBalanceResponse res = tuple2.getT1();
                        String orgTxId = tuple2.getT2();

                        if (!res.isStatus()) {
                            processedTxIds.put(orgTxId, "Operator not sending");
                            log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}", request.getGameId(),
                                    request.getTxnId());
                            // response.setRollbackTxnId(res.getProcessedTxId());
                        }

                        Wallet wallet = new Wallet();
                        wallet.setCurrency(cu.getCurrencyCode());
                        wallet.setTotalBalance(res.getBalance());
                        wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                                .total(wallet.getTotalBalance()));
                        wallet.setBonus(
                                new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));

                        response.setWallet(wallet);
                        response.setProcessedTxnIds(processedTxIds);
                        return response;
                    });
        }

        private Mono<Tuple2<GetBalanceResponse, String>> processTxn(String sessionToken,
                String txnId,
                String gameId,
                String player,
                CurrencyUnit cu,
                Double amount,
                TransactionType type,
                String gameRoundId,
                boolean roundClosed) {
            log.info("Slotegrator player service. process transaction {} amount {}", type, amount);

            PlayRequest playRequest = new PlayRequest();
            playRequest.setGame_id(gameId);
            playRequest.setRound_id(gameRoundId);
            playRequest.setSession_id(sessionToken);
            if (type == TransactionType.CREDIT) {
                playRequest.setAction("win");
                playRequest.setType("win");
                playRequest.setAmount(amount.toString());
                playRequest.setTransaction_id(txnId);
            } else if (type == TransactionType.DEBIT) {

                playRequest.setAction("bet");
                playRequest.setType("bet");
                playRequest.setAmount(amount.toString());
                playRequest.setTransaction_id(txnId);
            } else if (type == TransactionType.ROLLBACK) {
                playRequest.setAction("refund");
                playRequest.setTransaction_id(txnId);
            } else
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported transaction " + type);

            log.info("Slotegrator player service. process transaction request {}", playRequest);
            long startMillis = System.currentTimeMillis();

            return getWebClient()
                    .post()
                    .uri("/mplay")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(playRequest)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(GetBalanceResponse.class)
                    // .publishOn(Schedulers.parallel())
                    .flatMap(balanceResponse -> {
                        if (!balanceResponse.isStatus()) {
                            if (INSUFFICIENT_BALANCE.name().equals(balanceResponse.getCode()))
                                return Mono.error(new BaseException(INSUFFICIENT_BALANCE));
                            else if (SystemErrorCode.SYSTEM_ERROR.name().equals(balanceResponse.getCode()))
                                return Mono.error(new BaseException(SystemErrorCode.SYSTEM_ERROR));

                            return Mono.error(new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR,
                                    balanceResponse.getMessage()));
                        }
                        return Mono.just(balanceResponse);
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
                    .onErrorMap(throwable -> {
                        if (type == TransactionType.DEBIT
                                && !(throwable instanceof BaseException
                                        && (((BaseException) throwable).getErrorCode().equals(INSUFFICIENT_BALANCE)
                                                || ((BaseException) throwable).getErrorCode()
                                                        .equals(SystemErrorCode.SYSTEM_ERROR)))) {
                            return new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, throwable);
                        }
                        return throwable;
                    })
                    .doOnError(throwable -> {
                        log.error("Slotegrator transaction {} failed.", txnId, throwable);
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

        @Override
        public ClientHttpConnector getHttpConnector() {
            return new MessageSigningHttpConnector(getHttpClient());
        }

        @Override
        public void codecsConfigure(ClientCodecConfigurer clientDefaultCodecsConfigurer) {
            try {
                Signer signer = new Signer((String) getAttribute("clientSecret"));
                BodyProvidingJsonEncoder bodyProvidingJsonEncoder = new BodyProvidingJsonEncoder(signer, objectMapper);
                clientDefaultCodecsConfigurer.defaultCodecs().jackson2JsonEncoder(bodyProvidingJsonEncoder);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (InvalidKeyException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
