package in.aimlabs.gaming.gconnect.bfgames.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import in.aimlabs.gaming.AbstractConnectorWebClientBuilderService;
import in.aimlabs.gaming.services.PlayerAccountManager;
import in.aimlabs.gaming.services.PlayerAccountManagerFactory;
import in.aimlabs.gaming.dto.*;
import in.aimlabs.gaming.utils.PAMErrorsUtils;
import in.aimlabs.gaming.dto.GameSession;
import in.aimlabs.gaming.dto.GameSkin;
import in.aimlabs.gaming.dto.StakeSettings;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.connectors.Connector;
import in.aimlabs.gaming.services.*;
import in.aimlabs.money.currency.service.CurrencyService;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import in.aimlabs.rad.entity.Status;
import in.aimlabs.gaming.gconnect.bfgames.controller.BFGamesConnectController;
import in.aimlabs.gaming.gconnect.bfgames.exceptions.SessionExpiredException;
import in.aimlabs.gaming.gconnect.bfgames.signer.Sha224Signer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryQueries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
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
import reactor.util.function.Tuple2;
import reactor.util.retry.Retry;
import reactor.util.retry.RetrySpec;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Qualifier("bf-games")
@Slf4j
@Getter
@Component
public class BFGamesPlayerServiceAdaptor
        implements PlayerAccountManagerFactory {

    @Autowired
    WebClient.Builder webClientBuilder;
    @Autowired
    private HttpClient httpClient; // 1. Inject the shared HttpClient bean
    @Autowired
    IBrandGameService brandGameService;
    @Autowired
    INetworkService networkService;

    @Value("${rgs.player.connector.bf-games.uid:bf-games-connector}")
    String connectorUid;

    @Value("${rgs.player.connector.retries:3}")
    private String transactionRetries;

    @Autowired
    IGameSessionService gameSessionService;

    @Autowired
    IGameSettingsService gameSettingsService;

    @Autowired
    CurrencyService currencyService;

    @Autowired
    ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Autowired
    IGameSkinService gameSkinService;

    // @Autowired
    // TransactionService transactionService;

    @Autowired
    ObjectMapper objectMapper;

    public boolean supports(Connector connector) {
        return connectorUid.equals(connector.getUid())
                || connectorUid.equals(connector.getParentConnector());
    }

    @Override
    public PlayerAccountManager getInstance(Connector connector) {
        return new BFGamesPlayerServiceConnector(connector);
    }

    public Mono<List<BFGamesConnectController.GameData>> getGames(String currency) {
        return currencyService.getCurrency(currency)
                .flatMap(currencyUnit -> {
                    return networkService.findOneByConnector(connectorUid)
                            .flatMapMany(network -> {
                                return brandGameService.findAllByNetwork(network.getUid())
                                        .flatMap(brandGame -> {
                                            return Mono.zip(
                                                    gameSettingsService.findGameSettingsForCurrency(network.getTenant(),
                                                            brandGame.getBrand(),
                                                            brandGame.getGame(),
                                                            currency),
                                                    gameSkinService.findOne(brandGame.getGame()), Mono.just(brandGame));
                                        });
                            })
                            .map(tuple3 -> {
                                Map<String, Object> settings = tuple3.getT1();
                                log.info("licences {}", settings.get("licences"));
                                log.info("rtps {}", settings.get("rtps"));
                                StakeSettings stakeSettings = objectMapper
                                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                        .convertValue(settings, StakeSettings.class);
                                GameSkin gameSkin = tuple3.getT2();

                                BFGamesConnectController.GameData gameData = new BFGamesConnectController.GameData();
                                gameData.setId(gameSkin.getUid());

                                gameData.setName(gameSkin.getName());
                                gameData.setVersion(gameSkin.getClientVersion());
                                if (stakeSettings.getLadder() != null) {

                                    gameData.setLineBetSteps(Arrays.stream(stakeSettings.getLadder())
                                            .map(o -> Double.valueOf(o * 100).intValue()).toList());
                                    Integer minBet = gameData.getLineBetSteps().getFirst();
                                    Integer maxBet = gameData.getLineBetSteps().getLast();

                                    if (stakeSettings.getMinMaxLines() != null
                                            && stakeSettings.getMinMaxLines().length == 2)
                                        gameData.setBetRatio(stakeSettings.getMinMaxLines()[1]);

                                    gameData.setLicenses(new ArrayList<>());
                                    Double[] rtps = stakeSettings.getRtps();
                                    if (stakeSettings.getLicences() != null) {
                                        for (String licence : stakeSettings.getLicences()) {
                                            gameData.getLicenses()
                                                    .add(new BFGamesConnectController.GameData.LicenseDetails(licence,
                                                            rtps));
                                        }
                                    }

                                    gameData.setDefaultLineBet(gameData.getLineBetSteps().getFirst());
                                    gameData.setDefaultTotalBet(gameData.getDefaultLineBet() * gameData.getBetRatio());

                                    gameData.setMinTotalBet(minBet * gameData.getBetRatio());
                                    gameData.setMaxTotalBet(maxBet * gameData.getBetRatio());
                                }
                                return gameData;
                            }).collectList();
                });

    }

    class BFGamesPlayerServiceConnector extends AbstractConnectorWebClientBuilderService
            implements PlayerAccountManager {

        RetrySpec depositApiRetrySpec = Retry.max(3)
                .filter(e -> {
                    if (e instanceof BaseRuntimeException bre) {
                        return false;
                    } else
                        return e instanceof RuntimeException;
                });

        RetrySpec withdrawApiRetrySpec = Retry.max(3)
                .filter(e -> e instanceof TimeoutException);

        BFGamesPlayerServiceConnector(Connector connector) {
            super(webClientBuilder, connector, httpClient);
        }

        private Mono<Tuple2<GenericResponse, String>> processTxn(WebClient webClient,
                String token,
                String internalToken,
                String txnId,
                String gameId,
                String gameVersion,
                String player,
                CurrencyUnit cu,
                Double amount,
                TransactionType type,
                String gameRoundId,
                String orgTxnId,
                Double orgTxnAmount,
                boolean roundClosed) {

            GenericRequest.Args args = new GenericRequest.Args();
            GenericRequest playRequest = null;
            long minorAmount = Money.of(amount != null ? amount : 0D, cu).query(MonetaryQueries.convertMinorPart());
            /*
             * if (type == TransactionType.CREDIT && amount == 0) {
             * playRequest = new GenericRequest("deposit", new
             * GenericRequest.Mirror(UUID.randomUUID().toString()));
             * } else
             */ if (type == TransactionType.CREDIT) {
                playRequest = new GenericRequest("deposit", new GenericRequest.Mirror(UUID.randomUUID().toString()));
            } else if (type == TransactionType.DEBIT) {
                playRequest = new GenericRequest("withdraw", new GenericRequest.Mirror(UUID.randomUUID().toString()));
            } else if (type == TransactionType.ROLLBACK) {
                playRequest = new GenericRequest("rollback", new GenericRequest.Mirror(UUID.randomUUID().toString()));
                args.setWithdraw_action_id(orgTxnId);
                if (orgTxnAmount != null)
                    args.setWithdraw_amount(Money.of(orgTxnAmount, cu).query(MonetaryQueries.convertMinorPart()));
            } else
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported transaction " + type);

            playRequest.setArgs(args);

            addCallerIdAndPassword(args);
            args.setToken(token);
            args.setAmount(minorAmount);
            args.setCurrency(cu.getCurrencyCode());
            args.setGame_ref(gameId);
            if (gameVersion != null)
                args.setGame_ver(gameVersion);

            args.setRound_id(gameRoundId);
            args.setAction_id(txnId);
            args.setOffline(false);

            if (type == TransactionType.CREDIT)
                args.setEnd_round(roundClosed);

            // Not mandatory
            // args.setExternal_session_id(internalToken);

            playRequest.setArgs(args);

            long startMillis = System.currentTimeMillis();
            AtomicReference<GenericRequest> genericRequestAtomicReference = new AtomicReference<>(playRequest);
            GenericRequest finalPlayRequest = playRequest;
            return webClient
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(genericRequestAtomicReference.get())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(GenericResponse.class)
                    // .doOnNext(genericResponse -> {
                    // if(type==TransactionType.DEBIT && "happy-hours".equals(gameId))
                    // throw new RuntimeException("Simulate run time error");
                    // })
                    .onErrorMap(throwable -> {
                        if (type == TransactionType.DEBIT) {
                            return new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, throwable);
                        }
                        return throwable;
                    })
                    .map(genericResponse -> {
                        String errorcode = genericResponse.getResult().errorcode;
                        if (errorcode == null) {
                            return genericResponse;
                        }
                        GenericRequest genericRequest = genericRequestAtomicReference.get();
                        if ("1000".equals(errorcode)) {
                            if (type == TransactionType.DEBIT)
                                throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND,
                                        genericResponse.result.toString());

                            // This should not occur ideally because error 1000 will not be returned for
                            // Rollback request.
                            else if (type == TransactionType.ROLLBACK)
                                throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR,
                                        "Rollback gameRound " + gameRoundId + " failed!");
                            else// This should not occur ideally because operator send 1000 errocode only for
                                // debit requests.
                                throw new BaseRuntimeException(PAMErrorCode.GENERAL_API_ERROR,
                                        "Cannot handle 1000 errorCode for txnType " + type);
                        } else if (errorcode.startsWith("200")
                                && !genericRequest.getArgs().isOffline() // try offline deposit only if it is not an
                                                                         // offline deposit request.
                                && (type == TransactionType.CREDIT || type == TransactionType.ROLLBACK)) {
                            throw new SessionExpiredException();// retry with offline deposit / rollback
                        } else if (errorcode.equals("4000") && type == TransactionType.DEBIT) {
                            throw new BaseRuntimeException(PAMErrorCode.INSUFFICIENT_BALANCE, errorcode);
                        }
                        // else if(errorcode.startsWith("300") || errorcode.startsWith("400"))
                        else if (errorcode.startsWith("300") || errorcode.startsWith("400")) {
                            throw new BaseRuntimeException(PAMErrorCode.GENERAL_API_ERROR, errorcode);
                        } else {
                            throw new BaseRuntimeException(PAMErrorCode.GENERAL_API_ERROR,
                                    "request failed with errorcode " + errorcode);
                        }
                    })
                    .doOnError(throwable -> {
                        GenericRequest genericRequest = genericRequestAtomicReference.get();
                        if (throwable instanceof SessionExpiredException && !genericRequest.getArgs().isOffline()) {
                            genericRequest.setMirror(new GenericRequest.Mirror(UUID.randomUUID().toString()));
                            genericRequest.getArgs()
                                    .setOffline(true)
                                    .setToken(getOfflineToken(getOfflineTokenKey(), getUser(),
                                            genericRequest.getArgs().getRound_id(),
                                            genericRequest.getArgs().getAction_id(),
                                            player));
                        }
                    })
                    .retryWhen(
                            (type == TransactionType.CREDIT || type == TransactionType.ROLLBACK) ? depositApiRetrySpec
                                    : withdrawApiRetrySpec)
                    .zipWith(Mono.just(txnId))
                    .tap(() -> new DefaultSignalListener<Tuple2<GenericResponse, String>>() {

                        @Override
                        public void doOnSubscription() throws Throwable {
                            log.info("BFgames player service. process transaction request {}", finalPlayRequest);
                        }

                        public void doFinally(SignalType signalType) throws Throwable {
                            if (signalType == SignalType.CANCEL)
                                log.info("Mono signalType {}. Transaction {} elapsed Time: {}ms", signalType, txnId,
                                        System.currentTimeMillis() - startMillis);
                        }

                        public void doOnError(Throwable error) throws Throwable {
                            // log.error("BFgames transaction {} failed.", txnId, error);
                        }

                        public void doOnNext(Tuple2<GenericResponse, String> response) throws Throwable {
                            log.info("{}. Elapsed Time: {}ms", response, System.currentTimeMillis() - startMillis);
                        }
                    });
        }

        private static String getOfflineToken(String offlineToken, String callerId, String roundId, String actionId,
                String player) {
            /*
             * String secret = getConnector().getSettings().getOrDefault(
             * "secret", "").toString();
             */

            try {
                Sha224Signer sha224Signer = new Sha224Signer("");
                String data = offlineToken + callerId + roundId + actionId + "_" + player;
                return sha224Signer.hash(data.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new BaseRuntimeException(SystemErrorCode.COM_ERROR,
                        "failed to generate offline token for roundId: " + roundId + ", actionId: " + actionId
                                + ", player: " + player);
            }
        }

        private void addCallerIdAndPassword(GenericRequest.Args args) {
            args.setCaller_id(getUser());
            args.setCaller_password(getPassword());
        }

        private String getPassword() {
            return getConnector().getSettings().getOrDefault("password", "QubitGamesSuperPassword!").toString();
        }

        private String getUser() {
            return getConnector().getSettings().getOrDefault("user", "qubitgames").toString();
        }

        private String getOfflineTokenKey() {
            return getConnector().getSettings().getOrDefault("offlineTokenKey", "QubitGamesOfflineTokenSecret")
                    .toString();
        }

        @Override
        public Mono<Void> closeSession(PlayerBalanceRequest request) {
            long startMillis = System.currentTimeMillis();

            GenericRequest.Args args = new GenericRequest.Args();
            addCallerIdAndPassword(args);
            args.setOperator_id("");
            args.setStatus("CLOSE");
            args.setToken(request.getToken());

            GenericRequest tokenAuthenticationRequest = new GenericRequest("terminate",
                    new GenericRequest.Mirror(UUID.randomUUID().toString()));
            tokenAuthenticationRequest.setArgs(args);

            return getWebClient()
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(tokenAuthenticationRequest)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(GenericResponse.class)
                    // .publishOn(Schedulers.parallel())
                    .tap(() -> new DefaultSignalListener<GenericResponse>() {

                        public void doFinally(SignalType terminationType) throws Throwable {
                            log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
                        }

                        public void doOnError(Throwable error) throws Throwable {
                            log.error("BFgames playerInfo request failed.", error);
                        }
                    })
                    .tap(() -> new DefaultSignalListener<GenericResponse>() {
                        @Override
                        public void doOnError(Throwable error) throws Throwable {
                            log.info("Error creating player initialise response object.", error);
                            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, error);
                        }
                    }).then();
        }

        public Mono<PlayerInitialiseResponse> playerInitialise(PlayerInitialiseRequest request) {
            long startMillis = System.currentTimeMillis();

            GenericRequest.Args args = new GenericRequest.Args();
            addCallerIdAndPassword(args);
            args.setToken(request.getSessionToken());

            GenericRequest tokenAuthenticationRequest = new GenericRequest("authenticateToken",
                    new GenericRequest.Mirror(UUID.randomUUID().toString()));
            tokenAuthenticationRequest.setArgs(args);

            return getWebClient()
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(tokenAuthenticationRequest)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(GenericResponse.class)
                    // .publishOn(Schedulers.parallel())

                    .map(genericResponse -> {

                        if (genericResponse.getResult().getErrorcode() != null
                                && !"0".equals(genericResponse.getResult().getErrorcode())) {
                            if ("2001".equals(genericResponse.getResult().getErrorcode())) {
                                throw new BaseRuntimeException(PAMErrorCode.TOKEN_EXPIRED);
                            }
                            if ("2003".equals(genericResponse.getResult().getErrorcode())) {
                                throw new BaseRuntimeException(PAMErrorCode.INVALID_REQUEST, "Player not logged in.");
                            } else if ("2002".equals(genericResponse.getResult().getErrorcode())) {
                                throw new BaseRuntimeException(PAMErrorCode.TOKEN_INVALID);
                            } else {
                                throw new BaseRuntimeException(PAMErrorCode.GENERAL_API_ERROR);
                            }
                        }

                        PlayerInitialiseResponse res;

                        res = new PlayerInitialiseResponse();
                        res.setPlayerId(genericResponse.getResult().player_id);
                        res.setCurrency(genericResponse.getResult().currency);
                        Money balance = Money.ofMinor(Monetary.getCurrency(genericResponse.getResult().currency),
                                genericResponse.getResult().getBalance());
                        res.setTotalBalance(balance.getNumberStripped());
                        res.setCash(new Balance().amount(balance.getNumberStripped()).onHold(BigDecimal.ZERO)
                                .total(balance.getNumberStripped()));
                        res.setBonus(
                                new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));
                        res.setExternalToken(request.getSessionToken());

                        return res;
                    })
                    .doOnNext(playerInitialiseResponse -> {
                        reactiveRedisTemplate.opsForList()
                                .rightPush("default-active-sessions", playerInitialiseResponse.getExternalToken())
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe();
                    })
                    .tap(() -> new DefaultSignalListener<PlayerInitialiseResponse>() {

                        public void doFinally(SignalType terminationType) throws Throwable {
                            log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
                        }

                        @Override
                        public void doOnSubscription() throws Throwable {
                            log.info("{}", request);
                        }

                        public void doOnError(Throwable error) throws Throwable {
                            log.error("Error creating player initialise response object.", error);
                            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR);
                        }
                    });
        }

        public Mono<Wallet> playerBalance(PlayerBalanceRequest request) {
            long startMillis = System.currentTimeMillis();

            GenericRequest.Args args = new GenericRequest.Args();
            addCallerIdAndPassword(args);
            args.setToken(request.getToken());
            args.setGame_ref(request.getGameId());
            args.setCurrency(request.getCurrency());
            args.setOperator_id(null);
            args.setExternal_session_id(request.getInternalToken());
            args.setTimestamp(System.currentTimeMillis());

            GenericRequest getBalanceRequest = new GenericRequest("getBalance",
                    new GenericRequest.Mirror(UUID.randomUUID().toString()));
            getBalanceRequest.setArgs(args);

            return getWebClient()
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(getBalanceRequest)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(GenericResponse.class)
                    // .publishOn(Schedulers.parallel())
                    .tap(() -> new DefaultSignalListener<GenericResponse>() {

                        public void doFinally(SignalType terminationType) throws Throwable {
                            log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
                        }

                        @Override
                        public void doOnNext(GenericResponse value) throws Throwable {
                            log.info("BFgames player balance response {}.", value);
                        }

                        public void doOnError(Throwable error) throws Throwable {
                            log.error("BFgames playerInfo request failed.", error);
                        }
                    })
                    .map(genericResponse -> {
                        Money balance = Money.ofMinor(Monetary.getCurrency(genericResponse.getResult().getCurrency()),
                                genericResponse.getResult().getBalance());

                        Wallet wallet = new Wallet();
                        wallet.setCurrency(genericResponse.getResult().getCurrency());
                        wallet.setCash(new Balance().amount(balance.getNumberStripped()).onHold(BigDecimal.ZERO)
                                .total(balance.getNumberStripped()));
                        wallet.setTotalBalance(balance.getNumberStripped());
                        return wallet;
                    })
                    .tap(() -> new DefaultSignalListener<>() {

                        public void doOnError(Throwable error) throws Throwable {
                            log.info("Error creating player balance response object.", error);
                            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, error);
                        }

                        @Override
                        public void doOnNext(Wallet value) throws Throwable {
                            log.info("BFgames player wallet response {}", value);
                        }
                    });
        }

        public Mono<PlayerTransactionResponse> playerTransaction(PlayerTransactionRequest request) {
            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            PlayerGameTransaction zero = new PlayerGameTransaction();
            zero.setAmount(BigDecimal.ZERO);
            TransactionType type = request.getRequestType();
            List<Mono<Tuple2<GenericResponse, String>>> txsFlux = new ArrayList<>();
            if (type == TransactionType.ROLLBACK) {
                txsFlux.add(processTxn(getWebClient(),
                        request.getToken(),
                        request.getInternalToken(),
                        request.getTxnId(),
                        request.getGameId(),
                        request.getGameVersion(),
                        request.getPlayerId(),
                        cu,
                        0D,
                        TransactionType.ROLLBACK, request.getGameRoundId(),
                        request.getOrgTxnUid(),
                        request.getOrgTxnAmount(),
                        true));
            } else {
                if (type == TransactionType.CLOSED || type == TransactionType.CREDIT) {
                    if (request.getCredit() == null || type == TransactionType.CLOSED) {
                        log.info("No wins for gameRound {}. sending zero wins request ", request.getGameRoundId());
                        txsFlux.add(processTxn(getWebClient(),
                                request.getToken(),
                                request.getInternalToken(),
                                request.getTxnId(),
                                request.getGameId(),
                                request.getGameVersion(),
                                request.getPlayerId(),
                                cu,
                                0d,
                                TransactionType.CREDIT, request.getGameRoundId(),
                                request.getOrgTxnUid(),
                                request.getOrgTxnAmount(),
                                request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                    } else {
                        txsFlux.add(processTxn(getWebClient(),
                                request.getToken(),
                                request.getInternalToken(),
                                request.getTxnId(),
                                request.getGameId(),
                                request.getGameVersion(),
                                request.getPlayerId(),
                                cu,
                                request.getCredit(),
                                request.getRequestType(), request.getGameRoundId(),
                                request.getOrgTxnUid(),
                                request.getOrgTxnAmount(),
                                request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));
                    }
                } else if (type == TransactionType.DEBIT) {
                    if (request.getDebit() == null) {
                        throw new BaseRuntimeException(PAMErrorCode.INVALID_REQUEST, "Debit amount is null");
                    }
                    Mono<Tuple2<GenericResponse, String>> responseMono = processTxn(getWebClient(),
                            request.getToken(),
                            request.getInternalToken(),
                            request.getTxnId(),
                            request.getGameId(),
                            request.getGameVersion(),
                            request.getPlayerId(),
                            cu,
                            request.getDebit(),
                            request.getRequestType(), request.getGameRoundId(),
                            request.getOrgTxnUid(),
                            request.getOrgTxnAmount(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED);
                    /*
                     * .onErrorResume(throwable -> {
                     * if(throwable instanceof BaseRuntimeException err){
                     * if(err.getErrorCode()==SystemErrorCode.ROLLBACK_GAME_ROUND){
                     *//*
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
                        *//*
                           * return rollback(request).then(Mono.error(new
                           * BaseRuntimeException(SystemErrorCode.GAME_ROUND_CANCELLED)));
                           * }
                           * }
                           * return Mono.error(throwable);
                           * });
                           */

                    txsFlux.add(responseMono);
                } else if (type == TransactionType.DEBIT_CREDIT) {
                    if (request.getDebit() == null) {
                        throw new BaseRuntimeException(PAMErrorCode.INVALID_REQUEST, "Debit amount is null");
                    } else if (request.getCredit() == null) {
                        throw new BaseRuntimeException(PAMErrorCode.INVALID_REQUEST, "Credit amount is null");
                    }

                    Mono<Tuple2<GenericResponse, String>> responseMono = processTxn(getWebClient(),
                            request.getToken(),
                            request.getInternalToken(),
                            request.getTxnId() + "_0",
                            request.getGameId(),
                            request.getGameVersion(),
                            request.getPlayerId(),
                            cu,
                            request.getDebit(),
                            TransactionType.DEBIT,
                            request.getGameRoundId(),
                            request.getOrgTxnUid(),
                            request.getOrgTxnAmount(),
                            false);
                    txsFlux.add(responseMono);

                    txsFlux.add(processTxn(getWebClient(),
                            request.getToken(),
                            request.getInternalToken(),
                            request.getTxnId() + "_1",
                            request.getGameId(),
                            request.getGameVersion(),
                            request.getPlayerId(),
                            cu,
                            request.getCredit(),
                            TransactionType.CREDIT,
                            request.getGameRoundId(),
                            request.getOrgTxnUid(),
                            request.getOrgTxnAmount(),
                            request.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED));

                } else {
                    throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR,
                            "unsupported requestType " + request.getRequestType());
                }

            }
            return Flux.concat(txsFlux)
                    .collectList()
                    .tap(() -> new DefaultSignalListener<List<Tuple2<GenericResponse, String>>>() {
                        @Override
                        public void doOnNext(List<Tuple2<GenericResponse, String>> value) throws Throwable {
                            log.info("res {}", value);

                            if (request.getRequestType() == TransactionType.ROLLBACK) {
                                log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}", request.getGameId(),
                                        request.getTxnId());
                            }
                        }
                    })
                    .map(tuple2s -> {
                        Map<String, Object> processedTxIds = new HashMap<>();
                        AtomicLong balance = new AtomicLong();
                        tuple2s.forEach(tuple2 -> {
                            GenericResponse res = tuple2.getT1();
                            String orgTxId = tuple2.getT2();

                            balance.set(res.getResult().getBalance());
                            if (res.getResult().transaction_id != null) {
                                processedTxIds.put(orgTxId, res.getResult().transaction_id);
                            }

                            /*
                             * if (request.getRequestType() == TransactionType.ROLLBACK) {
                             * log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}",
                             * request.getGameId(), request.getTxnId());
                             * }
                             */
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

                        return response;
                    })
                    .tap(() -> new DefaultSignalListener<PlayerTransactionResponse>() {
                        @Override
                        public void doOnNext(PlayerTransactionResponse value) throws Throwable {
                            log.info("Player transaction service response {}", value);
                        }

                        @Override
                        public void doOnError(Throwable error) throws Throwable {
                            log.error("failed!", error);
                        }
                    });
        }

        public Mono<PlayerTransactionResponse> rollback(PlayerTransactionRequest request) {

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());

            return processTxn(getWebClient(),
                    request.getToken(),
                    request.getInternalToken(),
                    request.getTxnId(),
                    request.getGameId(),
                    request.getGameVersion(),
                    request.getPlayerId(),
                    cu,
                    request.getDebit(),
                    TransactionType.ROLLBACK,
                    request.getGameRoundId(),
                    request.getOrgTxnUid(),
                    request.getOrgTxnAmount(),
                    true)
                    .map(tuple2 -> {
                        Map<String, Object> processedTxIds = new HashMap<>();
                        AtomicLong balance = new AtomicLong();

                        // log.info("tuple2 {}", tuple2);
                        GenericResponse res = tuple2.getT1();
                        String orgTxId = tuple2.getT2();
                        balance.set(res.getResult().getBalance());
                        if (res.getResult().getTransaction_id() != null) {
                            processedTxIds.put(orgTxId, res.getResult().getTransaction_id());
                            log.info("Rollback-ed gameRound {} successfully. rollbackTxnId {}", request.getGameId(),
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
        public Mono<Void> keepSessionAlive(GameSession session) {

            long startMillis = System.currentTimeMillis();

            GenericRequest.Args args = new GenericRequest.Args();
            addCallerIdAndPassword(args);
            args.setToken(session.getToken());
            args.setTimestamp(System.currentTimeMillis());

            GenericRequest getBalanceRequest = new GenericRequest("tokenRefresh",
                    new GenericRequest.Mirror(UUID.randomUUID().toString()));
            getBalanceRequest.setArgs(args);

            return getWebClient()
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(getBalanceRequest)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, PAMErrorsUtils::handleError)
                    .bodyToMono(GenericResponse.class)
                    // .publishOn(Schedulers.parallel())
                    .tap(() -> new DefaultSignalListener<GenericResponse>() {

                        public void doFinally(SignalType terminationType) throws Throwable {
                            log.info("Elapsed Time: {}ms", System.currentTimeMillis() - startMillis);
                        }

                        public void doOnError(Throwable error) throws Throwable {
                            log.error("BFgames playerInfo request failed.", error);
                        }
                    })
                    .doOnNext(genericResponse -> {

                        if (genericResponse.getResult().errorcode == null
                                && genericResponse.getResult().getToken() != null
                                && !genericResponse.getResult().getToken().equals(session.getToken())) {
                            String token = genericResponse.getResult().getToken();
                            GameSession newSession = session.copy();
                            newSession.setToken(token);
                            gameSessionService.create(newSession)
                                    .flatMap(oldSession -> {
                                        session.setStatus(Status.INACTIVE);
                                        return gameSessionService.update(session)
                                                .flatMap(gameSession -> {
                                                    return reactiveRedisTemplate.opsForList()
                                                            .rightPush("default-active-sessions",
                                                                    gameSession.getToken());
                                                });
                                    }).subscribeOn(Schedulers.boundedElastic())
                                    .subscribe();
                        }
                    })
                    .tap(() -> new DefaultSignalListener<>() {

                        public void doOnError(Throwable error) throws Throwable {
                            log.info("Error creating player initialise response object.", error);
                            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, error);
                        }
                    })
                    .then();
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class GenericRequest {
        String methodname;
        Mirror mirror;
        String version = "1.0";
        String type = "jsonwsp/request";

        Args args;

        GenericRequest(String methodname, Mirror mirror) {
            this.methodname = methodname;
            this.mirror = mirror;
        }

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        static class Args {
            String status;
            String caller_id;
            String caller_password;
            String token;
            String external_session_id;
            String operator_id;
            long timestamp;
            long original_timestamp;
            String currency;
            String game_ref;
            String game_ver = "1.0.0";
            long amount;
            long withdraw_amount;
            String withdraw_action_id;
            long withdraw_action_timestamp;
            String round_id;
            boolean offline;
            boolean end_round;
            String action_id;
            String bonus_id;
            boolean bonus_done;
            String bonus_instance_id;
            ProgressiveContribution[] progressive_contributions;
            ProgressiveWinning[] progressive_winnings;
        }

        @Data
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        static class Mirror {
            String id;
        }

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        static class ProgressiveContribution {
            String progressive_id;
            String amount;
            String denominator;
        }

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        static class ProgressiveWinning {
            String progressive_id;
            String amount;

            String pot_amount;
            String denominator;
        }
    }

    @Data
    static class GenericResponse {
        String methodname;
        Reflection reflection;
        String servicenumber;
        String servicename;
        String version = "1.0";
        String type = "jsonwsp/request";

        Result result;

        @Data
        static class Reflection {
            String id;
        }

        @Data
        static class Result {
            String errorcode;
            String currency;
            String token;
            int balance;
            String transaction_id;
            String player_id;
            String nickname;
        }

    }

}
