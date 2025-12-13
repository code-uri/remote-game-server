package in.aimlabs.gaming.gconnect.parimatch.services.impl;

import in.aimlabs.gaming.AbstractConnectorWebClientBuilderService;
import in.aimlabs.gaming.services.PlayerAccountManager;
import in.aimlabs.gaming.services.PlayerAccountManagerFactory;
import in.aimlabs.gaming.dto.*;
import in.aimlabs.gaming.utils.PAMErrorsUtils;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import in.aimlabs.gaming.gconnect.parimatch.dto.ParimatchPlayerInfoRequest;
import in.aimlabs.gaming.gconnect.parimatch.dto.ParimatchPlayerInfoResponse;
import in.aimlabs.gaming.gconnect.parimatch.dto.ParimatchTransactionRequest;
import in.aimlabs.gaming.gconnect.parimatch.dto.ParimatchTransactionResponse;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static reactor.util.retry.Retry.withThrowable;

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
    WebClient.Builder webClientBuilder;
    @Autowired
    private HttpClient httpClient; // 1. Inject the shared HttpClient bean

    @Override
    public boolean supports(Connector connector) {
        return connectorUid.equals(connector.getUid())
                || connectorUid.equals(connector.getParentConnector());
    }

    @Override
    public PlayerAccountManager getInstance(Connector connector) {
        return new PlayerServiceConnector(connector);
    }

    class PlayerServiceConnector extends AbstractConnectorWebClientBuilderService implements PlayerAccountManager {

        PlayerServiceConnector(Connector connector) {
            super(webClientBuilder, connector, httpClient);
        }

        public Mono<PlayerInitialiseResponse> playerInitialise(PlayerInitialiseRequest request) {
            long startMillis = System.currentTimeMillis();
            ParimatchPlayerInfoRequest infoRequest = new ParimatchPlayerInfoRequest();
            infoRequest.setCid((String) getAttribute("cid"));
            infoRequest.setSessionToken(request.getSessionToken());
            /*
             * return getWebClient()
             * .tap(() -> (TapOnNextSignalListener<WebClient>) webClient -> {
             * log.info("Request for game: {}", gameId);
             * log.info(" {}", infoRequest);
             * })
             * .flatMap(webClient -> {
             */

            return getWebClient()
                    .post()
                    .uri("/playerInfo")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Hub-Consumer", getXHubConsumer())
                    .bodyValue(infoRequest)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(ParimatchPlayerInfoResponse.class)
                    // .publishOn(Schedulers.parallel())
                    .retryWhen(withThrowable(Retry.anyOf(RuntimeException.class)
                            .exponentialBackoffWithJitter(Duration.ofSeconds(1), Duration.ofSeconds(5))
                            .retryMax(Long.parseLong(transactionRetries))))
                    .tap(() -> new DefaultSignalListener<ParimatchPlayerInfoResponse>() {

                        public void doFinally(SignalType terminationType) throws Throwable {
                            log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
                        }

                        public void doOnError(Throwable error) throws Throwable {
                            log.error("Parimatch playerInfo request failed.", error);
                        }
                    })
                    .map(playerInfo -> {
                        PlayerInitialiseResponse res;
                        res = new PlayerInitialiseResponse();
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

            String cid = (String) getAttribute("cid");
            TransactionType type = request.getRequestType();
            List<Mono<Tuple2<ParimatchTransactionResponse, String>>> txsFlux = new ArrayList<>();
            if (type == TransactionType.ROLLBACK) {
                txsFlux.add(processTxn(request.getTxnId(), request.getToken(),
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
                        txsFlux.add(processTxn(request.getTxnId(), request.getToken(),
                                request.getGameId(),
                                request.getPlayerId(),
                                cu,
                                0D,
                                request.getRequestType(), request.getGameRoundId(),
                                request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED,
                                cid));
                    } else {
                        txsFlux.add(processTxn(request.getTxnId(), request.getToken(),
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
                    txsFlux.add(processTxn(request.getTxnId(), request.getToken(),
                            request.getGameId(),
                            request.getPlayerId(),
                            cu,
                            request.getDebit(),
                            request.getRequestType(), request.getGameRoundId(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED, cid));
                }

            }
            return Flux.concat(txsFlux)
                    .collectList()
                    .map(tuple2s -> {
                        Map<String, Object> processedTxIds = new HashMap<>();
                        AtomicLong balance = new AtomicLong();
                        tuple2s.forEach(tuple2 -> {
                            // log.info("tuple2 {}", tuple2);
                            ParimatchTransactionResponse res = tuple2.getT1();
                            String orgTxId = tuple2.getT2();
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
                        // response.setWallet(wallet);
                        // log.info("parimatch service response {}", response);
                        return response;
                    });
        }

        public Mono<PlayerTransactionResponse> rollback(PlayerTransactionRequest request) {

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            String cid = (String) getAttribute("cid");

            String debitTxnId = request.getOrgTxnUid();
            Double amount = request.getDebit();

            TransactionType type = request.getRequestType();

            return processTxn(request.getTxnId(), request.getToken(),
                    request.getGameId(),
                    request.getPlayerId(),
                    cu,
                    amount,
                    TransactionType.ROLLBACK, request.getGameRoundId(),
                    true, cid)
                    .map(tuple2 -> {
                        Map<String, Object> processedTxIds = new HashMap<>();
                        AtomicLong balance = new AtomicLong();

                        // log.info("tuple2 {}", tuple2);
                        ParimatchTransactionResponse res = tuple2.getT1();
                        String orgTxId = tuple2.getT2();
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
                        // response.setWallet(wallet);
                        // log.info("parimatch service response {}", response);
                        return response;
                    });
        }

        private Mono<Tuple2<ParimatchTransactionResponse, String>> processTxn(String txnId,
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
                throw new BaseRuntimeException(PAMErrorCode.INVALID_REQUEST, "unsupported transaction " + type);

            ParimatchTransactionRequest parimatchRequest = new ParimatchTransactionRequest();
            parimatchRequest.setCid(cid);
            parimatchRequest.setSessionToken(token);
            parimatchRequest.setPlayerId(player);
            parimatchRequest.setProductId(gameId);
            parimatchRequest.setAmount((long) amount.doubleValue() * 100);
            parimatchRequest.setCurrency(cu.getCurrencyCode());
            parimatchRequest.setRoundClosed(roundClosed);
            parimatchRequest.setTxId(txnId);
            parimatchRequest.setRoundId(gameRoundId);

            log.info("Parimatch player service. process transaction request {}", parimatchRequest);
            long startMillis = System.currentTimeMillis();
            return getWebClient()
                    .post()
                    .uri(api)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Hub-Consumer", getXHubConsumer())
                    .bodyValue(parimatchRequest)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(ParimatchTransactionResponse.class)
                    // .publishOn(Schedulers.parallel())
                    .retryWhen(withThrowable(Retry.anyOf(type == TransactionType.CREDIT
                            ? Throwable.class
                            : RuntimeException.class)
                            .exponentialBackoffWithJitter(Duration.ofSeconds(1), Duration.ofSeconds(5))
                            .retryMax(Long.parseLong(transactionRetries))))
                    .zipWith(Mono.just(parimatchRequest.getTxId() == null ? parimatchRequest.getRoundId()
                            : parimatchRequest.getTxId()))
                    .doOnError(throwable -> {
                        log.error("Parimatch transaction {} failed.", parimatchRequest.getTxId(), throwable);
                    })
                    .doFinally(signalType -> {
                        log.info("Mono signalType {}. Transaction {} elapsed Time: {}ms", signalType,
                                parimatchRequest.getTxId(), System.currentTimeMillis() - startMillis);
                    })
                    .doOnNext(response -> {
                        log.info("{}. Elapsed Time: {}ms", response, System.currentTimeMillis() - startMillis);
                    });
        }
    }
}
