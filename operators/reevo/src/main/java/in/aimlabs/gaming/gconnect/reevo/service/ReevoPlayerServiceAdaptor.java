package in.aimlabs.gaming.gconnect.reevo.service;

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
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import in.aimlabs.gaming.gconnect.reevo.controller.ReevoConnectController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

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
    private RestClient restClient;

    @Autowired
    private ObjectMapper objectMapper;

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

    public ReevoConnectController.GetGameListResponse getGames(
            ReevoConnectController.GetGameListRequest request) {
        ReevoConnectController.GetGameListResponse res = new ReevoConnectController.GetGameListResponse();
        res.setError(1);
        res.setResponse(List.of());
        return res;
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

    class ReevoPlayerServiceConnector implements PlayerAccountManager {

        private final Connector connector;

        ReevoPlayerServiceConnector(Connector connector) {
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
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR, "Missing reevo baseUrl");
            }
            return v;
        }

        private String getCallerId() {
            String v = getSettingAsString("callerId");
            return (v != null && !v.isBlank()) ? v : CALLERID;
        }

        private String getCallerPassword() {
            String v = getSettingAsString("callerPassword");
            return (v != null && !v.isBlank()) ? v : CALLERID;
        }

        private String getUsername() {
            String v = getSettingAsString("username");
            return (v != null && !v.isBlank()) ? v : CALLERID;
        }

        private String getSalt() {
            String v = getSettingAsString("salt");
            return (v != null && !v.isBlank()) ? v : SALT;
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

        private <T> T executeWithRetry(String op, boolean retryAllThrowables, java.util.function.Supplier<T> supplier) {
            int maxRetries = Math.max(1, getMaxRetries());
            int attempt = 0;
            while (true) {
                attempt++;
                try {
                    return supplier.get();
                } catch (Throwable t) {
                    boolean canRetry = attempt < maxRetries && (retryAllThrowables || t instanceof RuntimeException);
                    if (!canRetry) {
                        if (t instanceof RuntimeException re) {
                            throw re;
                        }
                        throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, op + " failed", t);
                    }
                    long delayMs = computeBackoffMillis(attempt);
                    log.warn("Reevo retry op={} attempt={} delayMs={} error={}", op, attempt, delayMs, t.toString());
                    sleepQuietly(delayMs);
                }
            }
        }

        private GetBalanceResponse getForBalanceResponse(String fullUrl) {
            try {
                String out = restClient
                        .get()
                        .uri(fullUrl)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(String.class);

                if (out == null) {
                    throw new BaseRuntimeException(SystemErrorCode.EMPTY_RESPONSE, "reevo returned empty response");
                }

                try {
                    return objectMapper.readValue(out, GetBalanceResponse.class);
                } catch (JsonProcessingException e) {
                    log.info("Reevo Balance response json parse failed. {}", out, e);
                }
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR);

            } catch (RestClientResponseException e) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "reevo http " + e.getStatusCode() + " calling " + getBaseUrl(), e);
            } catch (RestClientException e) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "reevo error calling " + getBaseUrl(), e);
            }
        }

        private String buildSignedUrl(Map<String, String> requestMap) {
            String queryString;
            String key;
            try {
                queryString = buildQueryString(requestMap);
                key = generateSHA1(getSalt() + queryString);
            } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
                throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, "Invalid request", e);
            }

            String base = getBaseUrl();
            String sep = base.contains("?") ? "&" : "?";
            return base + sep + queryString + "&key=" + key;
        }

        @Override
        public PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request) {
            long startMillis = System.currentTimeMillis();

            Map<String, String> requestMap = new LinkedHashMap<>();
            requestMap.put("callerId", getCallerId());
            requestMap.put("callerPassword", getCallerPassword());
            requestMap.put("remote_id", request.getPlayer());
            requestMap.put("username", getUsername());
            requestMap.put("action", "balance");
            requestMap.put("game_id_hash", request.getGameId());
            requestMap.put("session_id", request.getSessionToken());
            requestMap.put("gamesession_id", request.getSessionToken());

            String url = buildSignedUrl(requestMap);

            GetBalanceResponse balanceResponse = executeWithRetry(
                    "balance",
                    false,
                    () -> getForBalanceResponse(url));

            try {
                if (balanceResponse.status != 200) {
                    throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, "Get Balance Failed!");
                }

                PlayerInitialiseResponse res = new PlayerInitialiseResponse();
                res.setCurrency(request.getCurrency());
                res.setPlayerId(request.getPlayer());

                BigDecimal total = balanceResponse.balance != null
                        ? new BigDecimal(balanceResponse.balance)
                        : BigDecimal.ZERO;
                res.setTotalBalance(total);
                res.setCash(new Balance().amount(total).onHold(BigDecimal.ZERO).total(total));
                res.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));
                res.setExternalToken(request.getSessionToken());
                return res;
            } finally {
                log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
            }
        }

        @Override
        public Wallet playerBalance(PlayerBalanceRequest request) {
            PlayerInitialiseRequest initialiseRequest = new PlayerInitialiseRequest();
            initialiseRequest.setTenant(request.getTenant());
            initialiseRequest.setBrand(request.getBrand());
            initialiseRequest.setGameId(request.getGameId());
            initialiseRequest.setSessionToken(request.getToken());
            initialiseRequest.setPlayer(request.getPlayer());
            initialiseRequest.setCurrency(request.getCurrency());

            return playerInitialise(initialiseRequest).getWallet();
        }

        @Override
        public PlayerTransactionResponse playerTransaction(PlayerTransactionRequest request) {
            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            TransactionType type = request.getRequestType();
            List<TxnResult> txs = new ArrayList<>();

            if (type == TransactionType.ROLLBACK) {
                txs.add(processTxn(
                        request.getToken(),
                        request.getTxnId(),
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        0D,
                        TransactionType.ROLLBACK,
                        request.getGameRoundId(),
                        true));
            } else if (type == TransactionType.CLOSED || type == TransactionType.CREDIT) {
                Double credit = (request.getCredit() == null || type == TransactionType.CLOSED) ? 0D
                        : request.getCredit();
                txs.add(processTxn(
                        request.getToken(),
                        request.getTxnId(),
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        credit,
                        TransactionType.CREDIT,
                        request.getGameRoundId(),
                        request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
            } else if (type == TransactionType.DEBIT) {
                if (request.getDebit() == null) {
                    throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Debit amount is null");
                }
                txs.add(processTxn(
                        request.getToken(),
                        request.getTxnId(),
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        request.getDebit(),
                        TransactionType.DEBIT,
                        request.getGameRoundId(),
                        request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
            } else if (type == TransactionType.DEBIT_CREDIT) {
                if (request.getDebit() == null) {
                    throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Debit amount is null");
                }
                if (request.getCredit() == null) {
                    throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Credit amount is null");
                }

                txs.add(processTxn(
                        request.getToken(),
                        request.getTxnId() + "_-1",
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        request.getDebit(),
                        TransactionType.DEBIT,
                        request.getGameRoundId(),
                        request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                txs.add(processTxn(
                        request.getToken(),
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

            Wallet wallet = new Wallet();
            wallet.setCurrency(cu.getCurrencyCode());
            Map<String, Object> processedTxIds = new HashMap<>();
            for (TxnResult tx : txs) {
                GetBalanceResponse res = tx.response();
                String orgTxId = tx.orgTxId();
                if (res.getStatus() == 200) {
                    wallet.setTotalBalance(
                            res.getBalance() != null ? new BigDecimal(res.getBalance()) : BigDecimal.ZERO);
                    processedTxIds.put(orgTxId, "Operator not sending");
                }
            }

            wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                    .total(wallet.getTotalBalance()));
            wallet.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO)
                    .total(BigDecimal.ZERO));

            response.setWallet(wallet);
            response.setProcessedTxnIds(processedTxIds);
            return response;
        }

        @Override
        public PlayerTransactionResponse rollback(PlayerTransactionRequest request) {
            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            TxnResult tx = processTxn(
                    request.getToken(),
                    request.getOrgTxnUid() != null ? request.getOrgTxnUid() : request.getTxnId(),
                    request.getGameId(),
                    request.getPlayerId(),
                    cu,
                    request.getDebit(),
                    TransactionType.ROLLBACK,
                    request.getGameRoundId(),
                    true);

            Map<String, Object> processedTxIds = new HashMap<>();
            GetBalanceResponse res = tx.response();
            if (res.getStatus() != 200) {
                throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, res.getMsg());
            }

            processedTxIds.put(tx.orgTxId(), "Operator not sending");
            Wallet wallet = new Wallet();
            wallet.setCurrency(cu.getCurrencyCode());
            wallet.setTotalBalance(res.getBalance() != null ? new BigDecimal(res.getBalance()) : BigDecimal.ZERO);
            wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                    .total(wallet.getTotalBalance()));
            wallet.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO)
                    .total(BigDecimal.ZERO));

            response.setWallet(wallet);
            response.setProcessedTxnIds(processedTxIds);
            return response;
        }

        private record TxnResult(GetBalanceResponse response, String orgTxId) {
        }

        private TxnResult processTxn(
                String sessionToken,
                String txnId,
                String gameId,
                String player,
                CurrencyUnit cu,
                Double amount,
                TransactionType type,
                String gameRoundId,
                boolean roundClosed) {

            Map<String, String> requestMap = new LinkedHashMap<>();
            requestMap.put("callerId", getCallerId());
            requestMap.put("callerPassword", getCallerPassword());
            requestMap.put("remote_id", player);
            requestMap.put("username", getUsername());
            requestMap.put("game_id_hash", gameId);
            requestMap.put("session_id", sessionToken);
            requestMap.put("gamesession_id", sessionToken);

            if (type == TransactionType.CREDIT) {
                requestMap.put("action", "credit");
            } else if (type == TransactionType.DEBIT) {
                requestMap.put("action", "debit");
            } else if (type == TransactionType.ROLLBACK) {
                requestMap.put("action", "rollback");
            } else {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported transaction " + type);
            }

            requestMap.put("transaction_id", txnId);
            requestMap.put("amount", String.valueOf(amount != null ? amount : 0D));
            requestMap.put("game_id", gameId);
            requestMap.put("round_id", gameRoundId);
            requestMap.put("gameplay_final", roundClosed ? "1" : "0");
            requestMap.put("is_freeround_bet", "0");
            requestMap.put("jackpot_contribution_in_amount", "0");

            String url = buildSignedUrl(requestMap);
            long startMillis = System.currentTimeMillis();

            boolean retryAllThrowables = (type == TransactionType.CREDIT || type == TransactionType.ROLLBACK);
            GetBalanceResponse res = executeWithRetry(
                    "transaction " + type,
                    retryAllThrowables,
                    () -> getForBalanceResponse(url));

            log.info("Reevo transaction {} elapsed Time: {}ms", txnId, System.currentTimeMillis() - startMillis);

            if (type == TransactionType.DEBIT && res.getStatus() != 200) {
                throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, res.getMsg());
            }

            return new TxnResult(res, txnId);
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
