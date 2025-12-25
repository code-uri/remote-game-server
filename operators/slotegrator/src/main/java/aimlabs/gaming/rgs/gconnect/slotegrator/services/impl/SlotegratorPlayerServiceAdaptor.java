package aimlabs.gaming.rgs.gconnect.slotegrator.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import aimlabs.gaming.rgs.gconnect.slotegrator.client.Signer;
import aimlabs.gaming.rgs.gconnect.slotegrator.client.SlotegratorSigningInterceptor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private record TxnResult(GetBalanceResponse response, String orgTxnId) {
    }

    class PlayerServiceConnector implements PlayerAccountManager {

        private final Connector connector;
        private final RestClient restClient;

        PlayerServiceConnector(Connector connector) {
            this.connector = connector;
            this.restClient = buildRestClient(connector);
        }

        private RestClient buildRestClient(Connector connector) {
            String secret = getSettingAsString(connector, "clientSecret");
            if (secret == null || secret.isBlank()) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR, "Missing slotegrator clientSecret");
            }
            try {
                Signer signer = new Signer(secret);
                return RestClient
                        .builder()
                        .requestInterceptor(new SlotegratorSigningInterceptor(signer))
                        .build();
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            }
        }

        private static String getSettingAsString(Connector connector, String key) {
            Object v = connector != null && connector.getSettings() != null ? connector.getSettings().get(key) : null;
            return v != null ? v.toString() : null;
        }

        private String getBaseUrl() {
            String v = connector != null ? connector.getBaseUrl() : null;
            if (v == null || v.isBlank()) {
                v = getSettingAsString(connector, "baseUrl");
            }
            if (v == null || v.isBlank()) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR, "Missing slotegrator baseUrl");
            }
            return v;
        }

        private String buildUrl(String path) {
            String root = getBaseUrl();
            if (root.endsWith("/") && path != null && path.startsWith("/")) {
                return root.substring(0, root.length() - 1) + path;
            }
            if (!root.endsWith("/") && path != null && !path.startsWith("/")) {
                return root + "/" + path;
            }
            return path == null ? root : root + path;
        }

        private int getMaxRetries() {
            try {
                return Integer.parseInt(transactionRetries);
            } catch (Exception ignored) {
                return 3;
            }
        }

        private static long computeBackoffMillis(int attempt) {
            return 1_000L;
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
                    log.warn("Slotegrator retry op={} attempt={} delayMs={} error={}", op, attempt, delayMs,
                            t.toString());
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
                            "slotegrator returned empty response for " + path);
                }
                log.info("Slotegrator response for {}: {}", path, response);
                return objectMapper.readValue(response, responseType);
            } catch (RestClientResponseException e) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "slotegrator http " + e.getStatusCode() + " calling " + path, e);
            } catch (RestClientException e) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "slotegrator error calling " + path, e);
            } catch (JsonMappingException e) {
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            } catch (JsonProcessingException e) {
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            }
        }

        @Override
        public PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request) {

            long startMillis = System.currentTimeMillis();
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("action", "balance");
            requestMap.put("session_id", request.getSessionToken());

            try {
                GetBalanceResponse balanceResponse = executeWithRetry(
                        "balance",
                        true,
                        () -> postForObject("/mplay", requestMap, GetBalanceResponse.class));

                log.info("Slotegrator Balance response. {}", balanceResponse);

                if (!balanceResponse.isStatus()) {
                    if (SESSION_NOT_FOUND.equals(balanceResponse.getCode())) {
                        throw new BaseRuntimeException(SystemErrorCode.TOKEN_EXPIRED);
                    }
                    if (SystemErrorCode.INSUFFICIENT_BALANCE.name().equals(balanceResponse.getCode())) {
                        throw new BaseRuntimeException(SystemErrorCode.INSUFFICIENT_BALANCE);
                    }
                    if (SystemErrorCode.SYSTEM_ERROR.name().equals(balanceResponse.getCode())) {
                        throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR);
                    }
                    throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, balanceResponse.getMessage());
                }

                PlayerInitialiseResponse res = new PlayerInitialiseResponse();
                res.setCurrency(request.getCurrency());
                res.setPlayerId(request.getPlayer());
                Money balance = Money.of(balanceResponse.getBalance(), Monetary.getCurrency(request.getCurrency()));
                res.setTotalBalance(balance.getNumberStripped());
                res.setCash(new Balance().amount(balance.getNumberStripped()).onHold(BigDecimal.ZERO)
                        .total(balance.getNumberStripped()));
                res.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO)
                        .total(BigDecimal.ZERO));
                res.setExternalToken(request.getSessionToken());
                return res;
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

            TransactionType type = request.getRequestType();

            List<TxnResult> results = new ArrayList<>();
            if (type == TransactionType.ROLLBACK) {
                results.add(processTxn(request.getToken(),
                        request.getTxnId(),
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        0D,
                        TransactionType.ROLLBACK, request.getGameRoundId(),
                        true));
            } else if (type == TransactionType.CLOSED || type == TransactionType.CREDIT) {
                Double creditAmt = request.getCredit() != null ? request.getCredit() : 0D;
                if (request.getCredit() == null) {
                    log.info("No wins for gameRound {}. sending zero wins request", request.getGameRoundId());
                }
                results.add(processTxn(request.getToken(),
                        request.getTxnId(),
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        creditAmt,
                        TransactionType.CREDIT, request.getGameRoundId(),
                        request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
            } else if (type == TransactionType.DEBIT) {
                if (request.getDebit() == null) {
                    throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Debit amount is null");
                }
                results.add(processTxn(request.getToken(),
                        request.getTxnId(),
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        request.getDebit(),
                        TransactionType.DEBIT, request.getGameRoundId(),
                        request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
            } else if (type == TransactionType.DEBIT_CREDIT) {
                if (request.getDebit() == null) {
                    throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Debit amount is null");
                }
                if (request.getCredit() == null) {
                    throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Credit amount is null");
                }
                results.add(processTxn(request.getToken(),
                        request.getTxnId() + "_-1",
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        request.getDebit(),
                        TransactionType.DEBIT, request.getGameRoundId(),
                        request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                results.add(processTxn(request.getToken(),
                        request.getTxnId() + "_1",
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        request.getCredit(),
                        TransactionType.CREDIT, request.getGameRoundId(),
                        request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
            } else {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported requestType " + type);
            }

            Wallet wallet = new Wallet();
            wallet.setCurrency(cu.getCurrencyCode());
            Map<String, Object> processedTxIds = new HashMap<>();
            for (TxnResult r : results) {
                GetBalanceResponse res = r.response;
                wallet.setTotalBalance(res.getBalance());
                if (res.isStatus()) {
                    processedTxIds.put(r.orgTxnId, "Operator not sending");
                    if (request.getRequestType() == TransactionType.ROLLBACK) {
                        log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}",
                                request.getGameId(), request.getTxnId());
                    }
                }
            }

            wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                    .total(wallet.getTotalBalance()));
            wallet.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));
            response.setWallet(wallet);
            response.setProcessedTxnIds(processedTxIds);
            return response;

        }

        @Override
        public PlayerTransactionResponse rollback(PlayerTransactionRequest request) {

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            TxnResult r = processTxn(request.getToken(),
                    request.getOrgTxnUid(),
                    request.getGameId(),
                    request.getPlayerId(),
                    cu,
                    0D,
                    TransactionType.ROLLBACK,
                    request.getGameRoundId(),
                    true);

            Map<String, Object> processedTxIds = new HashMap<>();
            GetBalanceResponse res = r.response;
            if (!res.isStatus()) {
                throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, res.getMessage());
            }
            processedTxIds.put(r.orgTxnId, "Operator not sending");
            log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}", request.getGameId(),
                    request.getTxnId());

            Wallet wallet = new Wallet();
            wallet.setCurrency(cu.getCurrencyCode());
            wallet.setTotalBalance(res.getBalance());
            wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                    .total(wallet.getTotalBalance()));
            wallet.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));

            response.setWallet(wallet);
            response.setProcessedTxnIds(processedTxIds);
            return response;
        }

        private TxnResult processTxn(String sessionToken,
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

            boolean isDebit = type == TransactionType.DEBIT;
            try {
                GetBalanceResponse balanceResponse = executeWithRetry(
                        "mplay:" + type,
                        isDebit,
                        () -> postForObject("/mplay", playRequest, GetBalanceResponse.class));

                if (!balanceResponse.isStatus()) {
                    if (SystemErrorCode.INSUFFICIENT_BALANCE.name().equals(balanceResponse.getCode())) {
                        throw new BaseRuntimeException(SystemErrorCode.INSUFFICIENT_BALANCE);
                    }
                    if (SystemErrorCode.SYSTEM_ERROR.name().equals(balanceResponse.getCode())) {
                        throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR);
                    }
                    throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, balanceResponse.getMessage());
                }

                return new TxnResult(balanceResponse, txnId);
            } catch (RuntimeException e) {
                log.error("Slotegrator transaction {} failed.", txnId, e);
                if (isDebit) {
                    if (e instanceof BaseRuntimeException bre &&
                            (bre.getErrorCode() == SystemErrorCode.INSUFFICIENT_BALANCE
                                    || bre.getErrorCode() == SystemErrorCode.SYSTEM_ERROR)) {
                        throw e;
                    }
                    throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, e);
                }
                throw e;
            } finally {
                log.info("Slotegrator txnId={} type={} elapsedMs={}", txnId, type,
                        System.currentTimeMillis() - startMillis);
            }
        }
    }
}
