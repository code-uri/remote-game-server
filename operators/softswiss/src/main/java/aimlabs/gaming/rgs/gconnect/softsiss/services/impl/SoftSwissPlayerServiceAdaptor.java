package aimlabs.gaming.rgs.gconnect.softsiss.services.impl;

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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import aimlabs.gaming.rgs.gconnect.softsiss.client.Signer;
import aimlabs.gaming.rgs.gconnect.softsiss.client.SoftSwissSigningInterceptor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryQueries;
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
import java.util.function.Supplier;

@Qualifier("softswiss")
@Slf4j
@Getter
@Component
public class SoftSwissPlayerServiceAdaptor implements PlayerAccountManagerFactory {

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

    private record TxnResult(PlayResponse response, String orgTxnId) {
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
        String original_action_id;

        public Rollback(String action, String action_id, String original_action_id) {
            super(action, action_id);
            this.original_action_id = original_action_id;
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
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR, "Missing softswiss clientSecret");
            }
            try {
                Signer signer = new Signer(secret);
                return RestClient
                        .builder()
                        .requestInterceptor(new SoftSwissSigningInterceptor(signer))
                        .build();
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            }
        }

        private String getBaseUrl() {
            String v = connector != null ? connector.getBaseUrl() : null;
            if (v == null || v.isBlank()) {
                v = getSettingAsString(connector, "baseUrl");
            }
            if (v == null || v.isBlank()) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR, "Missing softswiss baseUrl");
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

        private <T> T executeWithRetry(String op, boolean shouldRetry, Supplier<T> supplier) {
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
                    log.warn("SoftSwiss retry op={} attempt={} error={}", op, attempt, t.toString());
                    try {
                        Thread.sleep(1_000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "Interrupted", ie);
                    }
                }
            }
        }

        private <TReq, TRes> TRes postForObject(String path, TReq body, Class<TRes> responseType,
                                                TransactionType txnTypeForErrorHandling) {
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
                            "softswiss returned empty response for " + path);
                }
                return objectMapper.readValue(response, responseType);
            } catch (RestClientResponseException e) {
                if (txnTypeForErrorHandling == TransactionType.DEBIT && !e.getStatusCode().is4xxClientError()) {
                    throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND,
                            new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR,
                                    "softswiss http " + e.getStatusCode() + " calling " + path, e));
                }
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "softswiss http " + e.getStatusCode() + " calling " + path, e);
            } catch (RestClientException e) {
                if (txnTypeForErrorHandling == TransactionType.DEBIT) {
                    throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, e);
                }
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "softswiss error calling " + path, e);
            } catch (JsonProcessingException e) {
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            }
        }

        private TxnResult processTxn(String txnId,
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

            if (type == TransactionType.CREDIT && (amount == null || amount == 0D)) {
                api = "/play";
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
            } else {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported transaction " + type);
            }

            boolean retry = type == TransactionType.DEBIT;
            PlayResponse playResponse = executeWithRetry(
                    "softswiss:" + type,
                    retry,
                    () -> postForObject(api, playRequest, PlayResponse.class, type));

            if (playResponse == null || playResponse.getBalance() == null) {
                if (type == TransactionType.DEBIT) {
                    throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND,
                            "softswiss returned invalid response for " + api);
                }
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR);
            }
            return new TxnResult(playResponse, orgTxnId);
        }

        @Override
        public PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request) {
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("user_id", request.getPlayer());
            requestMap.put("game", request.getGameId());
            requestMap.put("currency", request.getCurrency());

            PlayResponse playerInfo = postForObject("/play", requestMap, PlayResponse.class, null);
            if (playerInfo == null || playerInfo.getBalance() == null) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR);
            }

            PlayerInitialiseResponse res = new PlayerInitialiseResponse();
            res.setCurrency(request.getCurrency());
            res.setPlayerId(request.getPlayer());
            Money balance = Money.ofMinor(Monetary.getCurrency(request.getCurrency()), playerInfo.getBalance());
            res.setTotalBalance(balance.getNumberStripped());
            res.setCash(new Balance().amount(balance.getNumberStripped()).onHold(BigDecimal.ZERO)
                    .total(balance.getNumberStripped()));
            res.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));
            res.setExternalToken(request.getSessionToken());
            return res;
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
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            TransactionType type = request.getRequestType();
            List<TxnResult> results = new ArrayList<>();

            if (type == TransactionType.ROLLBACK) {
                results.add(processTxn(
                        request.getTxnId(),
                        request.getGameId(),
                        request.getPlayerId(),
                        cu,
                        0D,
                        TransactionType.ROLLBACK,
                        request.getGameRoundId(),
                        request.getOrgTxnUid(),
                        true));
            } else {
                if (type == TransactionType.CREDIT) {
                    Double credit = request.getCredit() != null ? request.getCredit() : 0D;
                    results.add(processTxn(
                            request.getTxnId(),
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            credit,
                            TransactionType.CREDIT,
                            request.getGameRoundId(),
                            request.getOrgTxnUid(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                }

                if (type == TransactionType.DEBIT && request.getDebit() != null) {
                    results.add(processTxn(
                            request.getTxnId(),
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            request.getDebit(),
                            TransactionType.DEBIT,
                            request.getGameRoundId(),
                            request.getOrgTxnUid(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                }
            }

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            Map<String, Object> processedTxIds = new HashMap<>();
            Long lastBalanceMinor = null;

            for (TxnResult r : results) {
                PlayResponse res = r.response();
                lastBalanceMinor = res.getBalance();
                if (res.getTransactions() != null && !res.getTransactions().isEmpty()) {
                    processedTxIds.put(r.orgTxnId(), res.getTransactions());
                }
                if (request.getRequestType() == TransactionType.ROLLBACK) {
                    log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}", res.getGame_id(),
                            request.getTxnId());
                }
            }

            if (lastBalanceMinor == null) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR);
            }

            Wallet wallet = new Wallet();
            wallet.setCurrency(cu.getCurrencyCode());
            wallet.setTotalBalance(Money.ofMinor(cu, lastBalanceMinor).getNumberStripped());
            wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                    .total(wallet.getTotalBalance()));
            wallet.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));

            response.setWallet(wallet);
            response.setProcessedTxnIds(processedTxIds);
            return response;
        }

        @Override
        public PlayerTransactionResponse rollback(PlayerTransactionRequest request) {
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            TxnResult r = processTxn(
                    request.getTxnId(),
                    request.getGameId(),
                    request.getPlayerId(),
                    cu,
                    request.getDebit(),
                    TransactionType.ROLLBACK,
                    request.getGameRoundId(),
                    request.getOrgTxnUid(),
                    true);

            PlayResponse res = r.response();
            Map<String, Object> processedTxIds = new HashMap<>();
            if (res.getTransactions() != null && !res.getTransactions().isEmpty()) {
                processedTxIds.put(r.orgTxnId(), res.getTransactions());
            }

            Wallet wallet = new Wallet();
            wallet.setCurrency(cu.getCurrencyCode());
            wallet.setTotalBalance(Money.ofMinor(cu, res.getBalance()).getNumberStripped());
            wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                    .total(wallet.getTotalBalance()));
            wallet.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            response.setWallet(wallet);
            response.setProcessedTxnIds(processedTxIds);
            return response;
        }
    }

    private static String getSettingAsString(Connector connector, String key) {
        Object v = connector != null && connector.getSettings() != null ? connector.getSettings().get(key) : null;
        return v != null ? v.toString() : null;
    }
}