package in.aimlabs.gaming.gconnect.reevo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import in.aimlabs.gaming.gconnect.reevo.controller.ReevoConnectController;
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
import reactor.netty.http.client.HttpClient;
import reactor.retry.Retry;
import reactor.util.function.Tuple2;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;

import static reactor.util.retry.Retry.withThrowable;

@Component
@Qualifier("reevo")
@Slf4j
@Getter
public class ReevoPlayerServiceAdaptor
        implements PlayerAccountManagerFactory {

    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";

    @Value("${rgs.player.connector.reevo.uid:reevo-connector}")
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
        return new ReevoPlayerServiceConnector(connector);
    }

    public Mono<ReevoConnectController.GetGameListResponse> getGames(
            ReevoConnectController.GetGameListRequest request) {

        return Mono.just(new ReevoConnectController.GetGameListResponse());
    }

    @Getter
    @Setter
    @ToString
    static class GetBalanceResponse {
        int status;
        String balance;
        String msg;
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

    class ReevoPlayerServiceConnector extends AbstractConnectorWebClientBuilderService implements PlayerAccountManager {

        ReevoPlayerServiceConnector(Connector connector) {
            super(webClientBuilder, connector, httpClient);
        }

        public Mono<PlayerInitialiseResponse> playerInitialise(PlayerInitialiseRequest request) {

            long startMillis = System.currentTimeMillis();
            Map<String, String> requestMap = new HashMap<>();

            requestMap.put("callerId", (String) getConnector().getSettings().getOrDefault("callerId", CALLERID));
            requestMap.put("callerPassword",
                    (String) getConnector().getSettings().getOrDefault("callerPassword", CALLERID));

            requestMap.put("remote_id", request.getPlayer());
            requestMap.put("username", (String) getConnector().getSettings().getOrDefault("username", CALLERID));

            requestMap.put("action", "balance");
            requestMap.put("game_id_hash", request.getGameId());
            requestMap.put("session_id", request.getSessionToken());
            requestMap.put("gamesession_id", request.getSessionToken());

            String queryString = "";
            String key = "";
            try {
                String salt = (String) getConnector().getSettings().getOrDefault("salt", SALT);
                queryString = buildQueryString(requestMap);
                key = generateSHA1(salt + queryString);
            } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
                log.error("Player initialise failed", e);
                return Mono.error(new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, "Invalid request"));
            }

            String finalQueryString = queryString;
            String finalKey = key;
            /*
             * return Mono.deferContextual(contextView -> {
             * // Set traceId in MDC to make it available to logs
             * String traceId = contextView.getOrDefault("traceId", "default-trace");
             * MDC.put("traceId", traceId);
             */
            ///api/seamless/s2x/qg?
            return getWebClient()
                    .get()
                    .uri("?" + finalQueryString + "&key=" + finalKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(String.class)
                    // .publishOn(Schedulers.parallel())
                    .tap(() -> new TapOnNextSignalListener<String>() {

                        public void doOnNext(String json) throws Throwable {
                            log.info("Reevo Balance response json. {}", json);
                        }
                    })
                    .map(s -> {
                        try {
                            return objectMapper.readValue(s, GetBalanceResponse.class);
                        } catch (JsonProcessingException e) {
                            log.info("Reevo Balance response json parse failed. {}", s, e);
                        }
                        throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR);
                    })
                    .flatMap(balanceResponse -> {
                        if (balanceResponse.status != 200) {

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
                    .tap(() -> new DefaultSignalListener<GetBalanceResponse>() {

                        public void doFinally(SignalType terminationType) throws Throwable {
                            log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
                        }

                        public void doOnError(Throwable error) throws Throwable {
                            log.error("Reevo playerInfo request failed.", error);
                        }
                    })
                    .tap(() -> new TapOnNextSignalListener<GetBalanceResponse>() {

                        public void doOnNext(GetBalanceResponse getBalanceResponse) throws Throwable {
                            log.info("Reevo Balance response. {}", getBalanceResponse);
                        }
                    })
                    .map(playerInfo -> {
                        PlayerInitialiseResponse res;
                        try {
                            res = new PlayerInitialiseResponse();
                            res.setCurrency(request.getCurrency());
                            res.setPlayerId(request.getPlayer());

                            BigDecimal balanceAmt = new BigDecimal(playerInfo.getBalance());

                            Money balance = Money.of(balanceAmt.doubleValue(),
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
            /*
             * .doFinally(signalType -> {
             * // Clear MDC after request completes
             * MDC.remove("traceId");
             * });
             * }).contextCapture();
             */
        }

        public Mono<Wallet> playerBalance(PlayerBalanceRequest request) {
            PlayerInitialiseRequest initialiseRequest = new PlayerInitialiseRequest();
            initialiseRequest.setTenant(request.getTenant());
            initialiseRequest.setBrand(request.getBrand());

            initialiseRequest.setGameId(request.getGameId());
            initialiseRequest.setSessionToken(request.getToken());
            initialiseRequest.setPlayer(request.getPlayer());
            initialiseRequest.setCurrency(request.getCurrency());

            return playerInitialise(initialiseRequest)
                    .map(PlayerInitialiseResponse::getWallet);
        }

        public Mono<PlayerTransactionResponse> playerTransaction(PlayerTransactionRequest request) {
            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            PlayerGameTransaction zero = new PlayerGameTransaction();
            zero.setAmount(BigDecimal.ZERO);

            TransactionType type = request.getRequestType();
            List<Mono<Tuple2<GetBalanceResponse, String>>> txsFlux = new ArrayList<>();

            // request.setTxnId(request.getTxnId().replaceAll("-", ""));
            /*
             * if (request.getOrgTxnUid() != null)
             * request.setOrgTxnUid(request.getOrgTxnUid().replaceAll("-", ""));
             */
            // request.setGameRoundId(request.getGameRoundId().replaceAll("-", ""));

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
                if (type == TransactionType.CLOSED || type == TransactionType.CREDIT) {
                    if (request.getCredit() == null || type == TransactionType.CLOSED) {
                        log.info("No wins for gameRound {}. sending zero wins request ", request.getGameRoundId());
                        txsFlux.add(processTxn(request.getToken(),
                                request.getTxnId(),
                                request.getGameId(),
                                request.getPlayerId(),
                                cu,
                                0D,
                                TransactionType.CREDIT, request.getGameRoundId(),
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
                } else if (type == TransactionType.DEBIT && request.getDebit() != null) {

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
                } else if (type == TransactionType.DEBIT_CREDIT) {
                    if (request.getDebit() == null) {
                        throw new BaseRuntimeException(PAMErrorCode.INVALID_REQUEST, "Debit amount is null");
                    } else if (request.getCredit() == null) {
                        throw new BaseRuntimeException(PAMErrorCode.INVALID_REQUEST, "Credit amount is null");
                    }

                    Mono<Tuple2<GetBalanceResponse, String>> debitMono = processTxn(request.getToken(),
                            request.getTxnId().concat("_-1"),
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
                            request.getTxnId() + "_1",
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            request.getCredit(),
                            TransactionType.CREDIT,
                            request.getGameRoundId(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));

                } else {
                    throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR,
                            "unsupported requestType " + request.getRequestType());
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

                            if (res.getStatus() == 200) {
                                wallet.setTotalBalance(new BigDecimal(res.getBalance()));
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

            // request.setTxnId(request.getTxnId().replaceAll("-", ""));
            /*
             * if (request.getOrgTxnUid() != null)
             * request.setOrgTxnUid(request.getOrgTxnUid().replaceAll("-", ""));
             * request.setGameRoundId(request.getGameRoundId().replaceAll("-", ""));
             */

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
                    .tap(() -> new DefaultSignalListener<Tuple2<GetBalanceResponse, String>>() {
                        @Override
                        public void doOnNext(Tuple2<GetBalanceResponse, String> value) throws Throwable {
                            if (value.getT1().getStatus() == 200)
                                log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}", request.getGameId(),
                                        request.getTxnId());
                        }
                    })
                    .map(tuple2 -> {
                        Map<String, Object> processedTxIds = new HashMap<>();

                        // log.info("tuple2 {}", tuple2);
                        GetBalanceResponse res = tuple2.getT1();
                        String orgTxId = tuple2.getT2();

                        if (res.getStatus() == 200) {
                            processedTxIds.put(orgTxId, "Operator not sending");
                            // response.setRollbackTxnId(res.getProcessedTxId());
                            Wallet wallet = new Wallet();
                            wallet.setCurrency(cu.getCurrencyCode());
                            wallet.setTotalBalance(new BigDecimal(res.getBalance()));
                            wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                                    .total(wallet.getTotalBalance()));
                            wallet.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO)
                                    .total(BigDecimal.ZERO));

                            response.setWallet(wallet);
                            response.setProcessedTxnIds(processedTxIds);
                        } else {
                            throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, res.getMsg());
                        }

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

            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("callerId", (String) getConnector().getSettings().getOrDefault("callerId", CALLERID));
            requestMap.put("callerPassword",
                    (String) getConnector().getSettings().getOrDefault("callerPassword", CALLERID));

            requestMap.put("remote_id", player);
            requestMap.put("username", (String) getConnector().getSettings().getOrDefault("username", CALLERID));
            requestMap.put("game_id_hash", gameId);
            requestMap.put("session_id", sessionToken);
            requestMap.put("gamesession_id", sessionToken);

            if (type == TransactionType.CREDIT) {
                requestMap.put("action", "credit");
            } else if (type == TransactionType.DEBIT) {
                requestMap.put("action", "debit");
            } else if (type == TransactionType.ROLLBACK) {
                requestMap.put("action", "rollback");
            } else
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported transaction " + type);

            requestMap.put("transaction_id", txnId);
            requestMap.put("amount", String.valueOf(amount));
            requestMap.put("game_id", gameId);
            requestMap.put("round_id", gameRoundId);
            requestMap.put("gameplay_final", roundClosed ? "1" : "0");
            requestMap.put("is_freeround_bet", "0");
            requestMap.put("jackpot_contribution_in_amount", "0");

            long startMillis = System.currentTimeMillis();

            String queryString = "";
            String key = "";
            try {
                String salt = (String) getConnector().getSettings().getOrDefault("salt", SALT);
                queryString = buildQueryString(requestMap);
                key = generateSHA1(salt + queryString);
            } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
                log.error("Player initialise failed", e);
                return Mono.error(new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, "Invalid request"));
            }

            return getWebClient()
                    .get()
                    .uri("?" + queryString + "&key=" + key)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(GetBalanceResponse.class)
                    // .publishOn(Schedulers.parallel())
                    .flatMap(balanceResponse -> {
                        if (type == TransactionType.DEBIT && balanceResponse.getStatus() != 200) {
                            // if (INSUFFICIENT_BALANCE.name().equals(balanceResponse.getCode()))
                            // return Mono.error(new BaseException(INSUFFICIENT_BALANCE));
                            // else if
                            // (SystemErrorCode.SYSTEM_ERROR.name().equals(balanceResponse.getCode()))
                            // return Mono.error(new BaseException(SystemErrorCode.SYSTEM_ERROR));

                            return Mono.error(new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND,
                                    balanceResponse.getMsg()));
                        }
                        return Mono.just(balanceResponse);
                    })
                    .retryWhen(
                            withThrowable(
                                    Retry.anyOf(type == TransactionType.CREDIT || type == TransactionType.ROLLBACK
                                            ? Throwable.class
                                            : RuntimeException.class)
                                            .exponentialBackoffWithJitter(Duration.ofSeconds(1), Duration.ofSeconds(5))
                                            .retryMax(Long.parseLong(transactionRetries))))
                    .zipWith(Mono.just(txnId))
                    .tap(() -> new DefaultSignalListener<Tuple2<GetBalanceResponse, String>>() {
                        @Override
                        public void doOnError(Throwable error) throws Throwable {
                            log.error("Reevo transaction {} failed.", txnId, error);
                        }
                    })
                    .onErrorMap(throwable -> {
                        if (!(throwable instanceof BaseRuntimeException)) {
                            if (throwable instanceof reactor.retry.RetryExhaustedException retryError) {
                                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR, retryError.getCause());
                            } else {
                                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR, throwable);
                            }
                        }
                        return throwable;
                    })
                    .doFinally(signalType -> {
                        if (signalType == SignalType.CANCEL)
                            log.info("Mono signalType {}. Transaction {} elapsed Time: {}ms", signalType, txnId,
                                    System.currentTimeMillis() - startMillis);
                    })
                    .tap(() -> new DefaultSignalListener<Tuple2<GetBalanceResponse, String>>() {
                        @Override
                        public void doOnSubscription() throws Throwable {
                            // log.info("Reevo player service. process transaction {} amount {}", type,
                            // amount);
                            log.info("Reevo player service. process transaction request {}", requestMap);
                        }

                        @Override
                        public void doOnNext(Tuple2<GetBalanceResponse, String> response) throws Throwable {
                            log.info("{}. Elapsed Time: {}ms", response, System.currentTimeMillis() - startMillis);
                        }
                    });
        }

    }

    // Method to build query string from the map
    public static String buildQueryString(Map<String, String> data) throws UnsupportedEncodingException {
        StringJoiner queryString = new StringJoiner("&");
        Set<Map.Entry<String, String>> entrySet = data.entrySet();

        for (Map.Entry<String, String> entry : entrySet) {
            queryString.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.toString()) + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString()));
        }

        return queryString.toString();
    }

    public static String generateSHA1(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hashInBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));

        // Convert byte array into hexadecimal string
        StringBuilder sb = new StringBuilder();
        for (byte b : hashInBytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }
}
