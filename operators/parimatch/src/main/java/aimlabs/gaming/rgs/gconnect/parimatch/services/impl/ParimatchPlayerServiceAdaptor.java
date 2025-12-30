package aimlabs.gaming.rgs.gconnect.parimatch.services.impl;

import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gameoperators.Balance;
import aimlabs.gaming.rgs.gameoperators.PlayerAccountManager;
import aimlabs.gaming.rgs.gameoperators.PlayerAccountManagerFactory;
import aimlabs.gaming.rgs.gameoperators.PlayerBalanceRequest;
import aimlabs.gaming.rgs.gameoperators.PlayerInitialiseRequest;
import aimlabs.gaming.rgs.gameoperators.PlayerInitialiseResponse;
import aimlabs.gaming.rgs.gameoperators.PlayerTransactionRequest;
import aimlabs.gaming.rgs.gameoperators.PlayerTransactionResponse;
import aimlabs.gaming.rgs.gameoperators.Wallet;
import aimlabs.gaming.rgs.gamerounds.GameRoundStatusEnum;
import aimlabs.gaming.rgs.transactions.TransactionType;
import aimlabs.gaming.rgs.gconnect.parimatch.dto.ParimatchPlayerInfoRequest;
import aimlabs.gaming.rgs.gconnect.parimatch.dto.ParimatchPlayerInfoResponse;
import aimlabs.gaming.rgs.gconnect.parimatch.dto.ParimatchTransactionRequest;
import aimlabs.gaming.rgs.gconnect.parimatch.dto.ParimatchTransactionResponse;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component
@Qualifier("parimatch")
@Slf4j
@Getter
public class ParimatchPlayerServiceAdaptor
        implements PlayerAccountManagerFactory {

    @Value("${athena.player.connector.parimatch.uid:parimatch-connector}")
    String connectorUid;

    @Value("${athena.player.connector.parimatch.cid:parimatch}")
    String cid;

    @Value("${athena.player.connector.parimatch.x-hub-consumer:mplay}")
    String xHubConsumer;

    @Value("${athena.player.connector.parimatch.baseUrl:https://casino-int.betlab.com/eva/hub/slots/wallet}")
    String baseUrl;

    @Value("${athena.player.connector.retries:3}")
    private String transactionRetries;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Override
    public boolean supports(Connector connector) {
        return connectorUid.equals(connector.getUid())
                || connectorUid.equals(connector.getParentConnector());
    }

    @Override
    public PlayerAccountManager getInstance(Connector connector) {
        return new PlayerServiceConnector(connector);
    }

    class PlayerServiceConnector implements PlayerAccountManager {

        private final Connector connector;
        private final RestClient restClient;

        PlayerServiceConnector(Connector connector) {
            this.connector = connector;
            this.restClient = restClientBuilder.baseUrl(connector.getBaseUrl())
                    .defaultHeader( "X-Hub-Consumer", getXHubConsumer())
                    .build();
        }

        private String getSettingAsString(String key) {
            Object v = connector != null && connector.getSettings() != null ? connector.getSettings().get(key) : null;
            return v != null ? v.toString() : null;
        }

        private String getCid() {
            String v = getSettingAsString("cid");
            return (v != null && !v.isBlank()) ? v : ParimatchPlayerServiceAdaptor.this.cid;
        }

        private String getXHubConsumer() {
            String v = getSettingAsString("x-hub-consumer");
            if (v == null || v.isBlank()) {
                v = getSettingAsString("xHubConsumer");
            }
            return (v != null && !v.isBlank()) ? v : ParimatchPlayerServiceAdaptor.this.xHubConsumer;
        }

        private <TReq, TRes> TRes postForObject(String path, TReq body, Class<TRes> responseType) {
            long startMillis = System.currentTimeMillis();
            try {
                TRes response = restClient
                        .post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(responseType);

                if (response == null) {
                    throw new BaseRuntimeException(SystemErrorCode.EMPTY_RESPONSE,
                            "parimatch returned empty response for " + path);
                }

                log.info("HTTP {} -> {} elapsed={}ms", path, responseType.getSimpleName(),
                        System.currentTimeMillis() - startMillis);
                return response;
            } catch (RestClientResponseException e) {
                log.error("Parimatch HTTP error calling {} for body {}", path, body, e);
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "parimatch http " + e.getRawStatusCode() + " calling " + path, e);
            } catch (RestClientException e) {
                log.error("Parimatch REST error calling {} for body {}", path, body, e);
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "parimatch error calling " + path, e);
            } catch (BaseRuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.error("Parimatch unexpected error calling {} for body {}", path, body, e);
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR,
                        "parimatch unexpected error calling " + path, e);
            }
        }

        private int getMaxRetries() {
            try {
                return Integer.parseInt(transactionRetries);
            } catch (Exception ignored) {
                return 3;
            }
        }

        private static long computeBackoffMillis(int attempt) {
            long base = 1_000L;
            long max = 5_000L;
            long exp = base * (1L << Math.min(10, Math.max(0, attempt - 1)));
            long capped = Math.min(max, exp);
            long jitter = ThreadLocalRandom.current().nextLong(0, 251);
            return Math.min(max, capped + jitter);
        }

        private static void sleepQuietly(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "Interrupted", ie);
            }
        }

        private boolean shouldRetry(Throwable t, boolean retryAllThrowables) {
            if (!retryAllThrowables && !(t instanceof RuntimeException)) {
                return false;
            }

            if (t instanceof BaseRuntimeException bre) {
                if (bre.getErrorCode() == SystemErrorCode.INVALID_REQUEST) {
                    return false;
                }
            }

            return true;
        }

        private <T> T executeWithRetry(String op, boolean retryAllThrowables, Supplier<T> supplier) {
            int maxRetries = Math.max(1, getMaxRetries());
            int attempt = 0;
            while (true) {
                attempt++;
                try {
                    return supplier.get();
                } catch (Throwable t) {
                    boolean canRetry = attempt < maxRetries && shouldRetry(t, retryAllThrowables);
                    if (!canRetry) {
                        if (t instanceof RuntimeException re) {
                            throw re;
                        }
                        throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, op + " failed", t);
                    }

                    long delayMs = computeBackoffMillis(attempt);
                    log.warn("Parimatch retry op={} attempt={} delayMs={} error={}", op, attempt, delayMs,
                            t.toString());
                    sleepQuietly(delayMs);
                }
            }
        }

        private record TxnResult(ParimatchTransactionResponse response, String orgTxId) {
        }

        @Override
        public PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request) {
            long startMillis = System.currentTimeMillis();
            ParimatchPlayerInfoRequest infoRequest = new ParimatchPlayerInfoRequest();
            infoRequest.setCid(getCid());
            infoRequest.setSessionToken(request.getSessionToken());

            ParimatchPlayerInfoResponse playerInfo = executeWithRetry(
                    "playerInfo",
                    false,
                    () -> postForObject("/playerInfo", infoRequest, ParimatchPlayerInfoResponse.class)
            );

            try {
                PlayerInitialiseResponse res = new PlayerInitialiseResponse();
                res.setCurrency(playerInfo.getCurrency());
                res.setPlayerId(playerInfo.getPlayerId());

                res.setTotalBalance(
                        BigDecimal.valueOf(playerInfo.getBalance() / 100).setScale(2, RoundingMode.HALF_UP));
                res.setCash(new Balance().amount(res.getTotalBalance()).onHold(BigDecimal.ZERO)
                        .total(res.getTotalBalance()));
                res.setBonus(
                        new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));
                res.setExternalToken(request.getSessionToken());
                return res;
            } catch (RuntimeException e) {
                log.info("Error creating player initialise response object.", e);
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            } finally {
                log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
            }

        }

        @Override
        public Wallet playerBalance(PlayerBalanceRequest request) {
            PlayerInitialiseRequest playerInitialiseRequest = new PlayerInitialiseRequest();
            playerInitialiseRequest.setCurrency(request.getCurrency());
            playerInitialiseRequest.setPlayer(request.getPlayer());
            playerInitialiseRequest.setGameId(request.getGameId());
            playerInitialiseRequest.setSessionToken(request.getToken());
            return playerInitialise(playerInitialiseRequest).getWallet();
        }

        @Override
        public PlayerTransactionResponse playerTransaction(PlayerTransactionRequest request) {
            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            String cid = getCid();
            TransactionType type = request.getRequestType();
            List<TxnResult> processed = new ArrayList<>();
            if (type == TransactionType.ROLLBACK) {
                processed.add(processTxn(request.getTxnId(), request.getToken(),
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        0D,
                        TransactionType.ROLLBACK, request.getGameRoundId(),
                        true, cid));
            } else {
                if (type == TransactionType.CREDIT) {
                    if (request.getCredit() == null) {
                        log.info("No wins for gameRound {}. sending zero wins request ", request.getGameRoundId());
                        processed.add(processTxn(request.getTxnId(), request.getToken(),
                                request.getGameId(),
                                request.getPlayerId(),
                                cu,
                                0D,
                                request.getRequestType(), request.getGameRoundId(),
                                request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED,
                                cid));
                    } else {
                        processed.add(processTxn(request.getTxnId(), request.getToken(),
                                request.getGameId(),
                                request.getPlayerId(),
                                cu,
                                request.getCredit(),
                                request.getRequestType(), request.getGameRoundId(),
                                request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED,
                                cid));
                    }
                }
                if (type == TransactionType.DEBIT && request.getDebit() != null) {
                    if (request.getDebit() == null) {
                        request.setDebit(0D);
                    }
                    processed.add(processTxn(request.getTxnId(), request.getToken(),
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            request.getDebit(),
                            request.getRequestType(), request.getGameRoundId(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED, cid));
                }

            }

            Map<String, Object> processedTxIds = new HashMap<>();
            AtomicLong balance = new AtomicLong();
            processed.forEach(tx -> {
                ParimatchTransactionResponse res = tx.response();
                String orgTxId = tx.orgTxId();
                balance.set(res.getBalance());
                if (res.getProcessedTxId() != null) {
                    processedTxIds.put(orgTxId, res.getProcessedTxId());
                    if (request.getRequestType() == TransactionType.ROLLBACK) {
                        log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}",
                                res.getRoundId(), res.getProcessedTxId());
                        response.setRollbackTxnId(res.getProcessedTxId());
                    }
                }
            });

            Wallet wallet = new Wallet();
            wallet.setCurrency(cu.getCurrencyCode());
            wallet.setTotalBalance(
                    BigDecimal.valueOf(balance.get() / 100).setScale(2, RoundingMode.HALF_UP));
            wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                    .total(wallet.getTotalBalance()));
            wallet.setBonus(
                    new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));

            response.setWallet(wallet);
            response.setProcessedTxnIds(processedTxIds);
            return response;
        }

        @Override
        public PlayerTransactionResponse rollback(PlayerTransactionRequest request) {

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            String cid = getCid();
            Double amount = request.getDebit() != null ? request.getDebit() : 0D;

            TxnResult tx = processTxn(request.getTxnId(), request.getToken(),
                    request.getGameId(),
                    request.getPlayerId(),
                    cu,
                    amount,
                    TransactionType.ROLLBACK, request.getGameRoundId(),
                    true, cid);

            Map<String, Object> processedTxIds = new HashMap<>();
            AtomicLong balance = new AtomicLong();

            ParimatchTransactionResponse res = tx.response();
            String orgTxId = tx.orgTxId();
            balance.set(res.getBalance());
            if (res.getProcessedTxId() != null) {
                processedTxIds.put(orgTxId, res.getProcessedTxId());
                log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}", res.getRoundId(),
                        res.getProcessedTxId());
                response.setRollbackTxnId(res.getProcessedTxId());
            }

            Wallet wallet = new Wallet();
            wallet.setCurrency(cu.getCurrencyCode());
            wallet.setTotalBalance(
                    BigDecimal.valueOf(balance.get() / 100).setScale(2, RoundingMode.HALF_UP));
            wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                    .total(wallet.getTotalBalance()));
            wallet.setBonus(
                    new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));

            response.setWallet(wallet);
            response.setProcessedTxnIds(processedTxIds);
            return response;
        }

        private TxnResult processTxn(String txnId,
                String token,
                String gameId,
                String player,
                CurrencyUnit cu,
                Double amount,
                TransactionType type,
                String gameRoundId,
                boolean roundClosed,
                String cid) {
            log.info("Parimatch player service. process transaction {} amount {}", type, amount);
            String api;
            if (type == TransactionType.CREDIT)
                api = "/win";
            else if (type == TransactionType.DEBIT)
                api = "/bet";
            else if (type == TransactionType.ROLLBACK)
                api = "/cancel";
            else
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported transaction " + type);

            ParimatchTransactionRequest parimatchRequest = new ParimatchTransactionRequest();
            parimatchRequest.setCid(cid);
            parimatchRequest.setSessionToken(token);
            parimatchRequest.setPlayerId(player);
            parimatchRequest.setProductId(gameId);
                BigDecimal major = BigDecimal.valueOf(amount != null ? amount : 0D);
                long minor = major
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
                parimatchRequest.setAmount(minor);
            parimatchRequest.setCurrency(cu.getCurrencyCode());
            parimatchRequest.setRoundClosed(roundClosed);
            parimatchRequest.setTxId(txnId);
            parimatchRequest.setRoundId(gameRoundId);

            log.info("Parimatch player service. process transaction request {}", parimatchRequest);
            long startMillis = System.currentTimeMillis();

                boolean retryAllThrowables = (type == TransactionType.CREDIT);
                ParimatchTransactionResponse response = executeWithRetry(
                    "transaction " + api,
                    retryAllThrowables,
                    () -> postForObject(api, parimatchRequest, ParimatchTransactionResponse.class)
                );

                String orgTxId = parimatchRequest.getTxId() == null ? parimatchRequest.getRoundId() : parimatchRequest.getTxId();
                log.info("Transaction {} elapsed Time: {}ms", orgTxId, System.currentTimeMillis() - startMillis);
                return new TxnResult(response, orgTxId);
        }
    }
}
