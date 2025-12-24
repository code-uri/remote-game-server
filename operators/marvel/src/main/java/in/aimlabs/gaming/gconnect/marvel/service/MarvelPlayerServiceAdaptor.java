package in.aimlabs.gaming.gconnect.marvel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import in.aimlabs.gaming.gconnect.marvel.controller.MarvelConnectController;
import lombok.Getter;
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

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Qualifier("marvel")
@Slf4j
@Getter
public class MarvelPlayerServiceAdaptor
        implements PlayerAccountManagerFactory {

    public static final String TRANSACTION_SUCCESS = "success";

    @Value("${rgs.player.connector.marvel.uid:marvel-connector}")
    String connectorUid;

    @Value("${rgs.player.connector.retries:3}")
    private String transactionRetries;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private RestClient restClient;

    public boolean supports(Connector connector) {
        return connectorUid.equals(connector.getUid())
                || connectorUid.equals(connector.getParentConnector());
    }

    @Override
    public PlayerAccountManager getInstance(Connector connector) {
        return new MarvelPlayerServiceConnector(connector);
    }

    public MarvelConnectController.GetGameListResponse getGames(
            MarvelConnectController.GetGameListRequest request) {
        return new MarvelConnectController.GetGameListResponse();
    }

    static record GetBalanceRes(String type, String username, String status, String currency, String balance) {
    }

    static record GetBalanceReq(String type, String token) {
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

    private record TxnResult(PlayerBetResponse response, String orgTxnId) {
    }

    class MarvelPlayerServiceConnector implements PlayerAccountManager {

        private final Connector connector;

        MarvelPlayerServiceConnector(Connector connector) {
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
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR, "Missing marvel baseUrl");
            }
            return v;
        }

        private String buildUrl(String path) {
            String root = getBaseUrl();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return root;
            }
            if (root.endsWith("/") && path.startsWith("/")) {
                return root.substring(0, root.length() - 1) + path;
            }
            if (!root.endsWith("/") && !path.startsWith("/")) {
                return root + "/" + path;
            }
            return root + path;
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
                    log.warn("Marvel retry op={} attempt={} delayMs={} error={}", op, attempt, delayMs, t.toString());
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
                            "marvel returned empty response for " + (path == null ? "<root>" : path));
                }
                log.info("Marvel response for {}: {}", (path == null ? "<root>" : path), response);
                return objectMapper.readValue(response, responseType);
            } catch (RestClientResponseException e) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "marvel http " + e.getStatusCode() + " calling " + (path == null ? "<root>" : path), e);
            } catch (RestClientException e) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "marvel error calling " + (path == null ? "<root>" : path), e);
            } catch (JsonMappingException e) {
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            } catch (JsonProcessingException e) {
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            }
        }

        @Override
        public PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request) {

            long startMillis = System.currentTimeMillis();

            GetBalanceReq req = new GetBalanceReq("balance", request.getSessionToken());

            try {
                GetBalanceRes balanceRes = executeWithRetry(
                        "balance",
                        true,
                        () -> postForObject("", req, GetBalanceRes.class));

                log.info("Marvel Balance response. {}", balanceRes);

                if ("failure".equals(balanceRes.status)) {
                    throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, "Get Balance Failed!");
                }

                PlayerInitialiseResponse res = new PlayerInitialiseResponse();
                res.setCurrency(request.getCurrency());
                res.setPlayerId(request.getPlayer());

                BigDecimal balanceAmt = new BigDecimal(balanceRes.balance);
                Money balance = Money.of(balanceAmt.doubleValue(), Monetary.getCurrency(balanceRes.currency));
                res.setTotalBalance(balance.getNumberStripped());
                res.setCash(new Balance().amount(balance.getNumberStripped()).onHold(BigDecimal.ZERO)
                        .total(balance.getNumberStripped()));
                res.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO)
                        .total(BigDecimal.ZERO));
                res.setExternalToken(request.getSessionToken());
                res.setSupportsMultiCredits(false);
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
            List<TxnResult> results = new ArrayList<>();
            if (type == TransactionType.ROLLBACK) {
                results.add(processTxn(request.getToken(),
                        request.getTxnId(), request.getOrgTxnUid(),
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        0D,
                        TransactionType.ROLLBACK, request.getGameRoundId(),
                        true));
            } else {
                if (type == TransactionType.CLOSED || type == TransactionType.CREDIT) {
                    Double creditAmt = request.getCredit() != null ? request.getCredit() : 0D;
                    if (request.getCredit() == null) {
                        log.info("No wins for gameRound {}. sending zero wins request", request.getGameRoundId());
                    }
                    results.add(processTxn(request.getToken(),
                            request.getTxnId(), null,
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            creditAmt,
                            TransactionType.CREDIT, request.getGameRoundId(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                }
                if (type == TransactionType.DEBIT) {
                    if (request.getDebit() == null) {
                        throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Debit amount is null");
                    }
                    results.add(processTxn(request.getToken(),
                            request.getTxnId(), null,
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            request.getDebit(),
                            TransactionType.DEBIT, request.getGameRoundId(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                } else if (type == TransactionType.DEBIT_CREDIT) {
                    if (request.getDebit() == null) {
                        throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Debit amount is null");
                    } else if (request.getCredit() == null) {
                        throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Credit amount is null");
                    }

                    results.add(processTxn(request.getToken(),
                            request.getTxnId() + "_-1", null,
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            request.getDebit(),
                            TransactionType.DEBIT, request.getGameRoundId(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));

                    results.add(processTxn(request.getToken(),
                            request.getTxnId() + "_1", null,
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            request.getCredit(),
                            TransactionType.CREDIT,
                            request.getGameRoundId(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                }

            }

            Wallet wallet = new Wallet();
            wallet.setCurrency(cu.getCurrencyCode());
            Map<String, Object> processedTxIds = new HashMap<>();
            for (TxnResult tuple2 : results) {
                PlayerBetResponse res = tuple2.response;
                String orgTxId = tuple2.orgTxnId;
                if (TRANSACTION_SUCCESS.equals(res.status)) {
                    wallet.setTotalBalance(BigDecimal.valueOf(res.balance).setScale(10, RoundingMode.HALF_UP));
                    processedTxIds.put(orgTxId, "Operator not sending");
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

            TxnResult tuple2 = processTxn(request.getToken(),
                    request.getTxnId(), request.getOrgTxnUid(),
                    request.getGameId(),
                    request.getPlayerId(),
                    cu,
                    0D,
                    TransactionType.ROLLBACK,
                    request.getGameRoundId(),
                    true);

            Map<String, Object> processedTxIds = new HashMap<>();
            PlayerBetResponse res = tuple2.response;
            String orgTxId = tuple2.orgTxnId;

            if (TRANSACTION_SUCCESS.equals(res.status)) {
                processedTxIds.put(orgTxId, "Operator not sending");
                log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}", request.getGameId(),
                        request.getTxnId());

                Wallet wallet = new Wallet();
                wallet.setCurrency(cu.getCurrencyCode());
                wallet.setTotalBalance(BigDecimal.valueOf(res.balance).setScale(10, RoundingMode.HALF_UP));
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
        }

        private TxnResult processTxn(String sessionToken,
                String txnId, String originalTxnId,
                String gameId,
                String player,
                CurrencyUnit cu,
                Double amount,
                TransactionType type,
                String gameRoundId,
                boolean roundClosed) {

            log.info("Marvel player service. process transaction {} amount {}", type, amount);

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

            log.info("Marvel player service. process transaction request {}", betRequest);
            long startMillis = System.currentTimeMillis();

            boolean isDebit = type == TransactionType.DEBIT;
            try {
                PlayerBetResponse betResponse = executeWithRetry(
                        "processTxn:" + type,
                        isDebit,
                        () -> postForObject("", betRequest, PlayerBetResponse.class));

                log.info("Marvel transaction {} response {}", txnId, betResponse);

                if (isDebit && "failure".equals(betResponse.status)) {
                    throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND);
                }
                return new TxnResult(betResponse, txnId);
            } catch (RuntimeException e) {
                log.error("Marvel transaction {} failed.", txnId, e);
                if (isDebit) {
                    throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, e);
                }
                throw e;
            } finally {
                log.info("Marvel txnId={} type={} elapsedMs={}", txnId, type, System.currentTimeMillis() - startMillis);
            }
        }
    }
}
