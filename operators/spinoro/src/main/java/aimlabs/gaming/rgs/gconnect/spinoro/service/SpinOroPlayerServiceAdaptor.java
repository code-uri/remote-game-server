package aimlabs.gaming.rgs.gconnect.spinoro.service;

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
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Qualifier("spinoro")
@Slf4j
@Getter
public class SpinOroPlayerServiceAdaptor
        implements PlayerAccountManagerFactory {

    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    public static final String TRANSACTION_SUCCESS = "success";

    @Value("${rgs.player.connector.spinoro.uid:spinoro-connector}")
    String connectorUid;

    @Value("${rgs.player.connector.retries:3}")
    private String transactionRetries;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestClient restClient;

    public boolean supports(Connector connector) {
        return connectorUid.equals(connector.getUid())
                || connectorUid.equals(connector.getParentConnector());
    }

    @Override
    public PlayerAccountManager getInstance(Connector connector) {
        return new SprinOroPlayerServiceConnector(connector);
    }

    class SprinOroPlayerServiceConnector implements PlayerAccountManager {

        private final Connector connector;

        SprinOroPlayerServiceConnector(Connector connector) {
            this.connector = connector;
        }

        private String getSettingAsString(String key) {
            Object v = connector != null && connector.getSettings() != null ? connector.getSettings().get(key) : null;
            return v != null ? v.toString() : null;
        }

        private String getBaseUrl() {
            String v = connector != null ? connector.getBaseUrl() : null;
            if (v == null || v.isBlank()) {
                v = getSettingAsString("baseUrl");
            }
            if (v == null || v.isBlank()) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR, "Missing spinoro baseUrl");
            }
            return v;
        }

        private String buildUrl(String path) {
            String root = getBaseUrl();
            if (root.endsWith("/")) {
                root = root.substring(0, root.length() - 1);
            }
            String p = (path == null || path.isBlank()) ? "/" : path;
            if (!p.startsWith("/")) {
                p = "/" + p;
            }
            return root + p;
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
            long jitter = java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 251);
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

        private <T> T executeWithRetry(String op, boolean shouldRetry, java.util.function.Supplier<T> supplier) {
            int maxRetries = Math.max(1, getMaxRetries());
            int attempt = 0;
            while (true) {
                attempt++;
                try {
                    return supplier.get();
                } catch (Throwable t) {
                    boolean canRetry = shouldRetry && attempt < maxRetries;
                    if (!canRetry) {
                        if (t instanceof RuntimeException re) {
                            throw re;
                        }
                        throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, op + " failed", t);
                    }
                    long delayMs = computeBackoffMillis(attempt);
                    log.warn("SpinOro retry op={} attempt={} delayMs={} error={}", op, attempt, delayMs, t.toString());
                    sleepQuietly(delayMs);
                }
            }
        }

        private <TReq, TRes> TRes postForObject(String path, TReq body, Class<TRes> responseType) {
            String url = buildUrl(path);
            try {
                String response = restClient
                        .post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);

                if (response == null) {
                    throw new BaseRuntimeException(SystemErrorCode.EMPTY_RESPONSE,
                            "spinoro returned empty response for " + path);
                }
                log.info("SpinOro response for {}: {}", path, response);
                return objectMapper.readValue(response, responseType);
            } catch (RestClientResponseException e) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "spinoro http " + e.getStatusCode() + " calling " + path, e);
            } catch (RestClientException e) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "spinoro error calling " + path, e);
            } catch (JsonMappingException e) {
                 throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            } catch (JsonProcessingException e) {
                 throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            }
        }

        private static BigDecimal toDecimalAmount(long minorUnits) {
            return BigDecimal.valueOf(minorUnits)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        @Override
        public PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request) {
            long startMillis = System.currentTimeMillis();

            InitGameRequest initGameRequest = new InitGameRequest(
                    (String) connector.getSettings().getOrDefault("secret", "StagM00nSec122"),
                    request.getInternalToken(), request.getSessionToken(), request.getPlayer(), 1,
                    Integer.parseInt((String) connector.getSettings().getOrDefault("gameProviderId", "126")),
                    request.getGameId());

            InitGameResponse balanceRes = executeWithRetry(
                    "initGame",
                    true,
                    () -> postForObject("/initGame", initGameRequest, InitGameResponse.class));

            try {
                if (!balanceRes.success) {
                    throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, "Get Balance Failed!");
                }

                PlayerInitialiseResponse res = new PlayerInitialiseResponse();
                res.setCurrency(request.getCurrency());
                res.setPlayerId(request.getPlayer());
                res.setTotalBalance(toDecimalAmount(balanceRes.balance));

                BigDecimal cash = toDecimalAmount(balanceRes.cashBalance);
                BigDecimal bonus = toDecimalAmount(balanceRes.bonusBalance);
                res.setCash(new Balance().amount(cash).onHold(BigDecimal.ZERO).total(cash));
                res.setBonus(new Balance().amount(bonus).onHold(BigDecimal.ZERO).total(bonus));

                res.setExternalToken(initGameRequest.sessionId);
                return res;
            } finally {
                log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
            }
        }

        @Override
        public Wallet playerBalance(PlayerBalanceRequest request) {
            long startMillis = System.currentTimeMillis();

            InitGameRequest balanceReq = new InitGameRequest(
                    (String) connector.getSettings().getOrDefault("secret", "StagM00nSec122"),
                    request.getInternalToken(), request.getToken(), request.getPlayer(), 1,
                    Integer.parseInt((String) connector.getSettings().getOrDefault("gameProviderId", "126")),
                    request.getGameId());

            InitGameResponse balanceRes = executeWithRetry(
                    "balance",
                    true,
                    () -> postForObject("/balance", balanceReq, InitGameResponse.class));

            try {
                if (!balanceRes.success) {
                    throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, "Get Balance Failed!");
                }

                Wallet wallet = new Wallet();
                wallet.setCurrency(request.getCurrency());
                wallet.setTotalBalance(toDecimalAmount(balanceRes.balance));

                BigDecimal cash = toDecimalAmount(balanceRes.cashBalance);
                BigDecimal bonus = toDecimalAmount(balanceRes.bonusBalance);
                wallet.setCash(new Balance().amount(cash).onHold(BigDecimal.ZERO).total(cash));
                wallet.setBonus(new Balance().amount(bonus).onHold(BigDecimal.ZERO).total(bonus));
                return wallet;
            } finally {
                log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
            }
        }

        @Override
        public PlayerTransactionResponse playerTransaction(PlayerTransactionRequest request) {
            PlayerTransactionResponse response = new PlayerTransactionResponse();

            TransactionType type = request.getRequestType();
            List<TxnResult> results = new ArrayList<>();
            if (type == TransactionType.ROLLBACK) {
                results.add(processTxn(request.getToken(), request.getInternalToken(),
                        request.getTxnId(), null,
                        request.getGameId(),
                        request.getPlayerId(),
                        request.getOrgTxnAmount() != null ? request.getOrgTxnAmount() : 0D,
                        0D,
                        TransactionType.ROLLBACK, request.getGameRoundId(),
                        false, request.getCurrency()));
            } else if (type == TransactionType.CLOSED || type == TransactionType.CREDIT) {
                Double debit = request.getDebit() != null ? request.getDebit() : 0D;
                Double credit = request.getCredit() != null ? request.getCredit() : 0D;
                if (request.getCredit() == null) {
                    log.info("No wins for gameRound {}. sending zero wins request", request.getGameRoundId());
                }
                results.add(processTxn(request.getToken(), request.getInternalToken(),
                        request.getTxnId(), null,
                        request.getGameId(),
                        request.getPlayerId(),
                        debit,
                        credit,
                        TransactionType.CREDIT, request.getGameRoundId(),
                        request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED, request.getCurrency()));
            } else if (type == TransactionType.DEBIT) {
                if (request.getDebit() == null) {
                    throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Debit amount is null");
                }
                results.add(processTxn(request.getToken(), request.getInternalToken(),
                        request.getTxnId(), null,
                        request.getGameId(),
                        request.getPlayerId(),
                        request.getDebit(),
                        request.getCredit() != null ? request.getCredit() : 0D,
                        TransactionType.DEBIT, request.getGameRoundId(),
                        request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED, request.getCurrency()));
            } else if (type == TransactionType.DEBIT_CREDIT) {
                if (request.getDebit() == null) {
                    throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Debit amount is null");
                }
                if (request.getCredit() == null) {
                    throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Credit amount is null");
                }
                results.add(processTxn(request.getToken(), request.getInternalToken(),
                        request.getTxnId(), null,
                        request.getGameId(),
                        request.getPlayerId(),
                        request.getDebit(),
                        request.getCredit(),
                        TransactionType.DEBIT_CREDIT, request.getGameRoundId(),
                        request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED, request.getCurrency()));
            } else {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported requestType " + type);
            }

            Wallet wallet = new Wallet();
            wallet.setCurrency(request.getCurrency());
            Map<String, Object> processedTxIds = new HashMap<>();
            for (TxnResult r : results) {
                if (r.response.success) {
                    wallet.setTotalBalance(toDecimalAmount(r.response.balance));
                    BigDecimal cash = toDecimalAmount(r.response.cashBalance);
                    BigDecimal bonus = toDecimalAmount(r.response.bonusBalance);
                    wallet.setCash(new Balance().amount(cash).onHold(BigDecimal.ZERO).total(cash));
                    wallet.setBonus(new Balance().amount(bonus).onHold(BigDecimal.ZERO).total(bonus));
                    processedTxIds.put(r.orgTxId, "Operator not sending");
                }
            }

            response.setWallet(wallet);
            response.setProcessedTxnIds(processedTxIds);
            return response;
        }

        @Override
        public PlayerTransactionResponse rollback(PlayerTransactionRequest request) {
            PlayerTransactionResponse response = new PlayerTransactionResponse();

            TxnResult r = processTxn(request.getToken(), request.getInternalToken(),
                    request.getTxnId(), request.getOrgTxnUid(),
                    request.getGameId(),
                    request.getPlayerId(),
                    request.getDebit() != null ? request.getDebit() : 0D,
                    request.getCredit() != null ? request.getCredit() : 0D,
                    TransactionType.ROLLBACK,
                    request.getGameRoundId(),
                    false, request.getCurrency());

            if (!r.response.success) {
                throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR);
            }

            Wallet wallet = new Wallet();
            wallet.setCurrency(request.getCurrency());
            wallet.setTotalBalance(toDecimalAmount(r.response.balance));
            BigDecimal cash = toDecimalAmount(r.response.cashBalance);
            BigDecimal bonus = toDecimalAmount(r.response.bonusBalance);
            wallet.setCash(new Balance().amount(cash).onHold(BigDecimal.ZERO).total(cash));
            wallet.setBonus(new Balance().amount(bonus).onHold(BigDecimal.ZERO).total(bonus));

            Map<String, Object> processedTxIds = new HashMap<>();
            processedTxIds.put(r.orgTxId, "Operator not sending");
            response.setWallet(wallet);
            response.setProcessedTxnIds(processedTxIds);
            return response;
        }

        private record TxnResult(TransactionResponse response, String orgTxId) {
        }

        private TxnResult processTxn(String sessionToken,
                String internalToken,
                String txnId, String originalTxnId,
                String gameId,
                String player,
                @NonNull Double debit, @NonNull Double credit,
                TransactionType type,
                String gameRoundId,
                boolean roundClosed,
                String currency) {
            log.info("SpinOro player service. process transaction {} debit {} credit {}", type, debit, credit);
            long amount = 0;
            String requestPath;
            if (type == TransactionType.DEBIT) {
                requestPath = "/debit";
                amount = Double.valueOf(debit * 100).longValue();
                roundClosed = false;
            } else if (type == TransactionType.CREDIT) {
                requestPath = "/credit";
                amount = Double.valueOf(credit * 100).longValue();
            } else if (type == TransactionType.DEBIT_CREDIT) {
                requestPath = "/debitAndCredit";
            } else if (type == TransactionType.ROLLBACK) {
                requestPath = "/rollbackDebit";
                amount = Double.valueOf(debit * 100).longValue();
                roundClosed = false;
            } else {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported transaction " + type);
            }

            Object transactionRequest;
            if (type == TransactionType.DEBIT_CREDIT) {
                transactionRequest = new DebitCreditRequest((String) connector.getSettings()
                        .getOrDefault("secret", "StagM00nSec122"), internalToken, sessionToken, player, 1,
                        Integer.parseInt((String) connector.getSettings().getOrDefault("gameProviderId", "126")),
                        gameId, gameRoundId, List.of(
                                new Transaction("DEBIT", txnId + "_-1", null, currency,
                                        Double.valueOf(debit * 100D).longValue()),
                                new Transaction("CREDIT", txnId + "_1", null, currency,
                                        Double.valueOf(credit * 100D).longValue())),
                        roundClosed, null);
            } else {
                transactionRequest = new TransactionRequest((String) connector.getSettings()
                        .getOrDefault("secret", "StagM00nSec122"), internalToken, sessionToken, player, 1,
                        Integer.parseInt((String) connector.getSettings().getOrDefault("gameProviderId", "126")),
                        gameId, gameRoundId, null, txnId, currency,
                        amount, roundClosed, null);
            }

            log.info("SpinOro player service. process transaction request {}", transactionRequest);
            long startMillis = System.currentTimeMillis();

            boolean shouldRetry = type == TransactionType.DEBIT;
            TransactionResponse res;
            try {
                res = executeWithRetry(
                        "txn " + requestPath,
                        shouldRetry,
                        () -> postForObject(requestPath, transactionRequest, TransactionResponse.class));
            } catch (BaseRuntimeException bre) {
                if (type == TransactionType.DEBIT && isUncertainDebitFailure(bre)) {
                    throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND,
                            "Debit outcome uncertain. Triggering rollbackDebit.",
                            bre);
                }
                throw bre;
            }

            log.info("{}. Elapsed Time: {}ms", res, System.currentTimeMillis() - startMillis);

            if (type == TransactionType.DEBIT && !res.success) {
                throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, "Debit failed");
            }
            return new TxnResult(res, txnId);
        }

        private static boolean isUncertainDebitFailure(BaseRuntimeException bre) {
            if (bre == null || bre.getErrorCode() == null) {
                return false;
            }
            return bre.getErrorCode() == SystemErrorCode.COM_ERROR
                    || bre.getErrorCode() == SystemErrorCode.EMPTY_RESPONSE;
        }

    }

    public static record InitGameRequest(String secret, String sessionId, String securityToken, String playerId,
            int playMode,
            int gameProviderId, String providerGameId) {
    }

    public static record InitGameResponse(boolean success, long balance, long cashBalance, long bonusBalance,
            Map<String, Object> additionalData) {

    }

    public static record TransactionRequest(String secret, String sessionId, String securityToken, String playerId,
            int playMode, int gameProviderId, String providerGameId,
            String roundId, String secondaryRoundId, String transactionId, String currency, long amount,
            boolean closeRound,
            Map<String, Object> additionalData) {

    }

    public static record DebitCreditRequest(String secret, String sessionId, String securityToken, String playerId,
            int playMode, int gameProviderId, String providerGameId,
            String roundId,
            List<Transaction> transactions,
            boolean closeRound,
            Map<String, Object> additionalData) {

    }

    public static record Transaction(String type, String transactionId, String secondaryRoundId, String currency,
            long amount) {

    }

    public static record TransactionResponse(boolean success, String referenceId, long balance, long cashBalance,
            long bonusBalance, String currency,
            Map<String, Object> additionalData) {

    }

    public static record VerifyPlayerRequest(String secret, String securityToken, String playerId) {

    }

    public static record VerifyPlayerResponse(boolean success, boolean result) {

    }
}
