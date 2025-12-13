package in.aimlabs.gaming.gconnect.softsiss.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.aimlabs.gaming.AbstractConnectorWebClientBuilderService;
import in.aimlabs.gaming.services.PlayerAccountManager;
import in.aimlabs.gaming.services.PlayerAccountManagerFactory;
import in.aimlabs.gaming.dto.*;
import in.aimlabs.gaming.utils.PAMErrorsUtils;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.core.utils.TapOnNextSignalListener;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import in.aimlabs.gaming.gconnect.softsiss.client.BodyProvidingJsonEncoder;
import in.aimlabs.gaming.gconnect.softsiss.client.MessageSigningHttpConnector;
import in.aimlabs.gaming.gconnect.softsiss.client.Signer;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryQueries;
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
import java.util.concurrent.atomic.AtomicLong;

import static reactor.util.retry.Retry.withThrowable;

@Qualifier("softswiss")
@Slf4j
@Getter
@Component
public class SoftSwissPlayerServiceAdaptor
        implements PlayerAccountManagerFactory {

    @Autowired
    WebClient.Builder webClientBuilder;

    @Autowired
    private HttpClient httpClient; // 1. Inject the shared HttpClient bean

    @Value("${rgs.player.connector.softswiss.uid:softswiss-connector}")
    String connectorUid;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${rgs.player.connector.retries:3}")
    private String transactionRetries;

    public boolean supports(Connector connector) {
        return connectorUid.equals(connector.getUid())
                || connectorUid.equals(connector.getParentConnector());
    }

    @Override
    public PlayerAccountManager getInstance(Connector connector) {
        return new PlayerServiceConnector(connector);
    }

    private Mono<Tuple2<PlayResponse, String>> processTxn(WebClient webClient,
            String txnId,
            String gameId,
            String player,
            CurrencyUnit cu,
            Double amount,
            TransactionType type,
            String gameRoundId,
            String orgTxnId,
            boolean roundClosed) {
        log.info("SoftSwiss player service. process transaction {} amount {}", type, amount);
        String api;

        PlayRequest playRequest = new PlayRequest();
        playRequest.setUser_id(player);
        playRequest.setGame(gameId);
        playRequest.setCurrency(cu.getCurrencyCode());
        playRequest.setFinished(roundClosed);
        playRequest.setGame_id(gameRoundId);

        if (type == TransactionType.CREDIT && amount == 0) {
            api = "/play";
            /*
             * playRequest.setActions(List.of(new Action("win",
             * Money.of(amount.getAmount(), cu).query(MonetaryQueries.convertMinorPart()),
             * amount.getTxnId())));
             */
        } else if (type == TransactionType.CREDIT) {
            api = "/play";
            playRequest.setActions(List.of(new Action("win",
                    Money.of(amount, cu).query(MonetaryQueries.convertMinorPart()), txnId)));
        } else if (type == TransactionType.DEBIT) {
            api = "/play";
            playRequest.setActions(List.of(new Action("bet",
                    Money.of(amount, cu).query(MonetaryQueries.convertMinorPart()), txnId)));
        } else if (type == TransactionType.ROLLBACK) {
            api = "/rollback";
            playRequest.setActions(List.of(new Rollback("rollback", txnId, orgTxnId)));
        } else
            throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported transaction " + type);

        log.info("SoftSwiss player service. process transaction request {}", playRequest);
        long startMillis = System.currentTimeMillis();
        return webClient.post()
                .uri(api)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(playRequest)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()

                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    return clientResponse.bodyToMono(String.class)
                            .switchIfEmpty(Mono.defer(() -> {
                                if (type == TransactionType.DEBIT)
                                    return Mono.error(new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND,
                                            new BaseRuntimeException(SystemErrorCode.EMPTY_RESPONSE)));
                                else
                                    return Mono.error(new BaseRuntimeException(SystemErrorCode.COM_ERROR));
                            }))
                            .tap(() -> new TapOnNextSignalListener<String>() {

                                public void doOnNext(String json) throws Throwable {
                                    log.error("softswiss transaction error response {}", json);
                                }
                            })
                            .handle((json, synchronousSink) -> {
                                if (type == TransactionType.DEBIT && !clientResponse.statusCode().is4xxClientError())
                                    synchronousSink.next(new BaseRuntimeException(
                                            SystemErrorCode.ROLLBACK_GAME_ROUND,
                                            new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, json)));
                                else
                                    synchronousSink.next(new BaseRuntimeException(SystemErrorCode.COM_ERROR, json));
                            });
                })
                .bodyToMono(PlayResponse.class)
                // .publishOn(Schedulers.parallel())
                .flatMap(playResponse -> {
                    // log.info("{}", playResponse );
                    if (playResponse.getBalance() == null) {
                        if (type == TransactionType.DEBIT)
                            return Mono.error(new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND,
                                    new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR)));
                        else
                            return Mono.error(new BaseRuntimeException(SystemErrorCode.COM_ERROR));
                    } else {
                        return Mono.just(playResponse);
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
                .onErrorMap(throwable -> {
                    if (type == TransactionType.DEBIT) {
                        return new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, throwable);
                    }
                    return throwable;
                })
                .tap(() -> new DefaultSignalListener<Tuple2<PlayResponse, String>>() {

                    public void doFinally(SignalType signalType) throws Throwable {
                        if (signalType == SignalType.CANCEL)
                            log.info("Mono signalType {}. Transaction {} elapsed Time: {}ms", signalType, txnId,
                                    System.currentTimeMillis() - startMillis);
                    }

                    public void doOnError(Throwable error) throws Throwable {
                        log.error("SoftSwiss transaction {} failed.", txnId, error);
                    }

                    public void doOnNext(Tuple2<PlayResponse, String> response) throws Throwable {
                        log.info("{}. Elapsed Time: {}ms", response, System.currentTimeMillis() - startMillis);
                    }
                });
    }

    @Getter
    @Setter
    @ToString
    static class PlayRequest {
        String user_id;
        String currency;
        String game;
        String game_id;
        boolean finished;
        List<Action> actions;
    }

    @Getter
    @Setter
    @ToString
    static class PlayResponse {
        Long balance;
        String game_id;
        List<Transaction> transactions = new ArrayList<>();
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    static class Action {
        String action;
        long amount;
        String action_id;

        public Action(String action, String action_id) {
            this.action = action;
            this.action_id = action_id;
        }
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    static class Rollback extends Action {
        // String action;
        String original_action_id;
        // String action_id;

        public Rollback(String action, String action_id, String original_action_id) {
            super(action, action_id);
            this.original_action_id = original_action_id;
        }

        public String toString() {
            final StringBuilder sb = new StringBuilder("Rollback{");
            sb.append("action='").append(action).append('\'');
            sb.append(", amount=").append(amount);
            sb.append(", action_id='").append(action_id).append('\'');
            sb.append(", original_action_id='").append(original_action_id).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    static class Transaction {
        String action_id;
        String tx_id;
        String processed_at;
        // String bonus_amount;
    }

    class PlayerServiceConnector extends AbstractConnectorWebClientBuilderService implements PlayerAccountManager {

        PlayerServiceConnector(Connector connector) {
            super(webClientBuilder, connector, httpClient);
        }

        public Mono<PlayerInitialiseResponse> playerInitialise(PlayerInitialiseRequest request) {

            long startMillis = System.currentTimeMillis();

            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("user_id", request.getPlayer());
            requestMap.put("game", request.getGameId());
            requestMap.put("currency", request.getCurrency());

            return getWebClient()
                    .post()
                    .uri("/play")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestMap)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(PlayResponse.class)
                    // .publishOn(Schedulers.parallel())
                    .retryWhen(withThrowable(reactor.retry.Retry.anyOf(RuntimeException.class)
                            .exponentialBackoffWithJitter(Duration.ofSeconds(1), Duration.ofSeconds(5))
                            .retryMax(Long.parseLong(transactionRetries))))
                    .tap(() -> new DefaultSignalListener<PlayResponse>() {

                        public void doFinally(SignalType terminationType) throws Throwable {
                            log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
                        }

                        public void doOnError(Throwable error) throws Throwable {
                            log.error("SoftSwiss playerInfo request failed.", error);
                        }
                    })
                    .map(playerInfo -> {
                        PlayerInitialiseResponse res;

                        res = new PlayerInitialiseResponse();
                        res.setCurrency(request.getCurrency());
                        res.setPlayerId(request.getPlayer());
                        Money balance = Money.ofMinor(Monetary.getCurrency(request.getCurrency()),
                                playerInfo.getBalance());
                        res.setTotalBalance(balance.getNumberStripped());
                        res.setCash(new Balance().amount(balance.getNumberStripped()).onHold(BigDecimal.ZERO)
                                .total(balance.getNumberStripped()));
                        res.setBonus(
                                new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));
                        res.setExternalToken(request.getSessionToken());

                        return res;
                    })
                    .tap(() -> new DefaultSignalListener<PlayerInitialiseResponse>() {

                        public void doOnError(Throwable error) throws Throwable {
                            log.info("Error creating player initialise response object.", error);
                            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, error);
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
            List<Mono<Tuple2<PlayResponse, String>>> txsFlux = new ArrayList<>();
            if (type == TransactionType.ROLLBACK) {
                txsFlux.add(processTxn(getWebClient(), request.getTxnId(),
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        0D,
                        TransactionType.ROLLBACK, request.getGameRoundId(),
                        request.getOrgTxnUid(),
                        true));
            } else {
                if (type == TransactionType.CREDIT) {
                    if (request.getCredit() == null) {
                        log.info("No wins for gameRound {}. sending zero wins request ", request.getGameRoundId());
                        txsFlux.add(processTxn(getWebClient(), request.getTxnId(),
                                request.getGameId(),
                                request.getPlayerId(),
                                cu,
                                0d,
                                request.getRequestType(), request.getGameRoundId(),
                                request.getOrgTxnUid(),
                                request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                    } else {
                        txsFlux.add(processTxn(getWebClient(), request.getTxnId(),
                                request.getGameId(),
                                request.getPlayerId(),
                                cu,
                                request.getCredit(),
                                request.getRequestType(), request.getGameRoundId(),
                                request.getOrgTxnUid(),
                                request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                    }
                }
                if (type == TransactionType.DEBIT && request.getDebit() != null) {
                    /*
                     * if (request.getDebit() == null) {
                     * request.setDebit(zero);
                     * }
                     */
                    Mono<Tuple2<PlayResponse, String>> responseMono = processTxn(getWebClient(), request.getTxnId(),
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            request.getDebit(),
                            request.getRequestType(), request.getGameRoundId(),
                            request.getOrgTxnUid(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED);
                    /*
                     * .onErrorResume(throwable -> {
                     * if(throwable instanceof BaseRuntimeException err){
                     * if(err.getErrorCode()==SystemErrorCode.ROLLBACK_GAME_ROUND){
                     * txReq.setRequestType(TransactionType.ROLLBACK);
                     * return processTxn(webClient,
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

                    txsFlux.add(responseMono);
                }

            }
            return Flux.concat(txsFlux)
                    .collectList()
                    .map(tuple2s -> {
                        Map<String, Object> processedTxIds = new HashMap<>();
                        AtomicLong balance = new AtomicLong();
                        tuple2s.forEach(tuple2 -> {
                            // log.info("tuple2 {}", tuple2);
                            PlayResponse res = tuple2.getT1();
                            String orgTxId = tuple2.getT2();
                            balance.set(res.getBalance());
                            if (!res.getTransactions().isEmpty()) {
                                processedTxIds.put(orgTxId, res.getTransactions());

                            }

                            if (request.getRequestType() == TransactionType.ROLLBACK) {
                                log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}", res.getGame_id(),
                                        request.getTxnId());
                            }
                        });

                        Wallet wallet = new Wallet();
                        wallet.setCurrency(cu.getCurrencyCode());
                        wallet.setTotalBalance(Money.ofMinor(cu, balance.get()).getNumberStripped());
                        wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                                .total(wallet.getTotalBalance()));
                        wallet.setBonus(
                                new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));

                        response.setWallet(wallet);
                        response.setProcessedTxnIds(processedTxIds);
                        // response.setWallet(wallet);
                        // log.info("parimatch service response {}", response);
                        return response;
                    });
        }

        public Mono<PlayerTransactionResponse> rollback(PlayerTransactionRequest request) {

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            return processTxn(getWebClient(), request.getTxnId(),
                    request.getGameId(),
                    request.getPlayerId(),
                    cu,
                    request.getDebit(),
                    TransactionType.ROLLBACK, request.getGameRoundId(),
                    request.getOrgTxnUid(),
                    true)
                    .map(tuple2 -> {
                        Map<String, Object> processedTxIds = new HashMap<>();
                        AtomicLong balance = new AtomicLong();

                        // log.info("tuple2 {}", tuple2);
                        PlayResponse res = tuple2.getT1();
                        String orgTxId = tuple2.getT2();
                        balance.set(res.getBalance());
                        if (!res.getTransactions().isEmpty()) {
                            processedTxIds.put(orgTxId, res.getTransactions());
                            log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}", res.getGame_id(),
                                    request.getTxnId());
                            // response.setRollbackTxnId(res.getProcessedTxId());
                        }

                        Wallet wallet = new Wallet();
                        wallet.setCurrency(cu.getCurrencyCode());
                        wallet.setTotalBalance(Money.ofMinor(cu, balance.get()).getNumberStripped());
                        wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                                .total(wallet.getTotalBalance()));
                        wallet.setBonus(
                                new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));

                        response.setWallet(wallet);
                        response.setProcessedTxnIds(processedTxIds);
                        // response.setWallet(wallet);
                        // log.info("parimatch service response {}", response);
                        return response;
                    });
        }

        @Override
        public ClientHttpConnector getHttpConnector() {
            log.info("added shared httpclient");
            return new MessageSigningHttpConnector(getHttpClient());
        }

        @Override
        public void codecsConfigure(ClientCodecConfigurer clientDefaultCodecsConfigurer) {
            Signer signer = null;
            log.info("added softswiss signer: {}", signer);
            try {
                signer = new Signer((String) getAttribute("clientSecret"));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (InvalidKeyException e) {
                throw new RuntimeException(e);
            }

            final BodyProvidingJsonEncoder bodyProvidingJsonEncoder = new BodyProvidingJsonEncoder(signer);
            clientDefaultCodecsConfigurer.defaultCodecs().jackson2JsonEncoder(bodyProvidingJsonEncoder);
        }
    }
}