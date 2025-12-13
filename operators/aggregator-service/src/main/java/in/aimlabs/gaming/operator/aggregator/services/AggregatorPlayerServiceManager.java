package in.aimlabs.gaming.operator.aggregator.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.aimlabs.gaming.service.FreeSpinsAllotmentService;
import in.aimlabs.gaming.service.IPromotionService;
import in.aimlabs.gaming.services.PlayerAccountManager;
import in.aimlabs.gaming.dto.*;
import in.aimlabs.gaming.supplier.GameSupplierLocator;
import in.aimlabs.gaming.utils.PlayerWalletUtils;
import in.aimlabs.gaming.dto.GameRound;
import in.aimlabs.gaming.dto.GameSession;
import in.aimlabs.gaming.dto.Transaction;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import in.aimlabs.gaming.services.*;
import in.aimlabs.money.currency.service.CurrencyService;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.DefaultErrorCode;
import in.aimlabs.rad.entity.Status;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.observability.DefaultSignalListener;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Component
public class AggregatorPlayerServiceManager implements PlayerAccountManager {

    @Value("${app.player.mock.token.expiration:50}")
    String tokenExpiration;

    String connectorUid = "aggregator-connector";

    @Autowired
    @Lazy
    IGameRoundService gameRoundService;

    @Autowired
    @Lazy
    IGameSessionService gameSessionService;

    @Autowired
    @Lazy
    IPlayerService playerService;

    @Autowired
    @Lazy
    ITransactionService transactionService;

    @Autowired
    @Lazy
    IBrandService brandService;

    @Autowired
    @Lazy
    IGameSkinService gameSkinService;

    @Autowired
    @Lazy
    ObjectMapper objectMapper;

    /*
     * @Autowired
     * BrandGameService brandGameService;
     */
    /*
     * 
     * @Autowired
     * GameProviderService gameProviderService;
     */

    @Autowired
    FreeSpinsAllotmentService freeSpinsAllotmentService;

    @Autowired
    IConnectorService connectorService;

    @Autowired
    GameSupplierLocator gameSupplierLocator;

    @Autowired
    CurrencyService currencyService;

    @Autowired
    IPromotionService promotionService;

    @Override
    public Mono<PlayerTransactionResponse> playerTransaction(PlayerTransactionRequest request) {

        Mono<GameSession> gameSessionMono = gameSessionService.findOneByUid(request.getToken());
        Mono<GameRound> gameRoundMono = gameRoundService.findOneByTenantAndGameIdAndCorrelationId("default",
                request.getGameId(), request.getGameRoundId());

        if (request.getRequestType() == TransactionType.CREDIT || request.getRequestType() == TransactionType.ROLLBACK
                || request.getRequestType() == TransactionType.CLOSED) {
            // handle other transactions excluding DEBIT and DEBIT_CREDIT. rollback not
            // required.
            // no token validation of token. get token from gameRound
            return gameRoundMono
                    .switchIfEmpty(Mono.error(new BaseRuntimeException(SystemErrorCode.INVALID_GAME_ROUND)))
                    .flatMap(gameRound -> {
                        Mono<GameSession> gameSessionForGameRoundMono = null;
                        if (request.getToken() == null)
                            gameSessionForGameRoundMono = gameSessionService.findOneByUid(gameRound.getSession());
                        else
                            gameSessionForGameRoundMono = gameSessionMono;

                        return gameSessionForGameRoundMono.zipWith(playerService.findOneByUid(gameRound.getPlayer()))
                                .switchIfEmpty(
                                        Mono.error(new BaseRuntimeException(new DefaultErrorCode("token.not.found",
                                                "token.not.found", 404))))
                                .flatMap(tuple2 -> {
                                    GameSession gameSession = tuple2.getT1();
                                    // GameRound gameRound = tuple2.getT2();
                                    Player player = tuple2.getT2();
                                    return processTransaction(request, gameRound, gameSession, player, false)
                                            .onErrorResume(throwable -> {
                                                return gameRoundService
                                                        .updatePartial(gameRound.getUid(),
                                                                Map.of("status", Status.ERROR))
                                                        .then(Mono.error(throwable));
                                            })
                                            .contextWrite(context -> context.put("PAM_CONNECTOR",
                                                    gameSession.getPamConnector() != null));
                                });
                    })
                    .contextWrite(context -> context.put("TENANT", "default"))
                    .doOnError(throwable -> {
                        log.error("{}. Failed!", request, throwable);
                    });
        } else {
            // handle DEBIT and DEBIT_CREDIT request.
            // validate token case.
            return gameSessionMono
                    .switchIfEmpty(Mono.error(new BaseRuntimeException(new DefaultErrorCode("token.not.found",
                            "token.not.found", 404))))
                    .flatMap(gameSession -> {
                        return gameRoundMono
                                .flatMap(gameRound -> {
                                    // GameRound gameRound = tuple2.getT2();
                                    return playerService.findOneByUid(gameRound.getPlayer())
                                            .flatMap(player -> {
                                                return processTransaction(request, gameRound, gameSession, player,
                                                        false);
                                            });
                                })
                                .switchIfEmpty(Mono.defer(() -> {
                                    return handleNewGameRound(request, gameSession);
                                }))
                                .contextWrite(context -> context.put("PAM_CONNECTOR",
                                        gameSession.getPamConnector()));
                    })
                    .contextWrite(context -> context.put("TENANT", "default"));
            // .doOnError(throwable -> {
            // log.error("{}. Failed!", request, throwable);
            // });
        }
    }

    private Mono<PlayerTransactionResponse> processTransaction(PlayerTransactionRequest txn, GameRound gameRound,
            GameSession gameSession, Player player, boolean rollbackRequired) {

        if (txn.getRequestType() == TransactionType.ROLLBACK) {
            return handleRollbackGameRound(txn, gameRound, gameSession, player);
        } else {
            return transactionService
                    .handleTransaction(txn, txn.getTxnId(), gameRound, gameSession, player, rollbackRequired,
                            (txn.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED
                                    || txn.getGameRoundStatus() == GameRoundStatusEnum.CLOSED))
                    .onErrorResume(throwable -> {
                        if (throwable instanceof BaseRuntimeException err) {
                            if (err.getErrorCode() == SystemErrorCode.ROLLBACK_GAME_ROUND) {
                                return rollback(txn).then(
                                        Mono.error(new BaseRuntimeException(SystemErrorCode.GAME_ROUND_CANCELLED)));
                            }
                        }
                        return Mono.error(throwable);
                    })
                    .flatMap(response -> {
                        if (gameRound.getStatus() == Status.INPROGRESS
                                && txn.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED) {
                            gameRound.setStatus(Status.COMPLETED);
                        }
                        return updateGameRound(response, gameRound, gameSession);
                    });
        }
    }

    @Override
    public Mono<PlayerInitialiseResponse> playerInitialise(PlayerInitialiseRequest request) {
        // log.info("request received");
        // log.info("session {} headers {}", webExchange.getRequest().getQueryParams(),
        // webExchange.getRequest().getHeaders() );
        long startTime = System.currentTimeMillis();

        if (request.getSessionToken() == null) {
            return Mono
                    .error(new BaseRuntimeException(new DefaultErrorCode("TOKEN_NOT_FOUND", "TOKEN_NOT_FOUND", 404)));
        }

        String sessionToken = request.getSessionToken();
        return gameSessionService.findOneByUid(sessionToken)
                .switchIfEmpty(Mono.error(
                        new BaseRuntimeException(new DefaultErrorCode("token.not.found", "token.not.found", 404))))
                .flatMap(gameSession -> {

                    return playerService.findOneByUid(gameSession.getPlayer())
                            .flatMap(player -> {
                                return playerService.initialise(player.getNetwork(), gameSession.getUid(),
                                        gameSession.getToken(),
                                        gameSession.getPlayer(),
                                        player.getCorrelationId(),
                                        gameSession.getCurrency(),
                                        gameSession.getBrand(),
                                        gameSession.getGame(),
                                        true)
                                        .flatMap(playerInfo -> {
                                            PlayerInitialiseResponse response = new PlayerInitialiseResponse();
                                            response.setPlayerId(playerInfo.getUid());
                                            response.unWrapWallet(PlayerWalletUtils.asWallet(playerInfo.getWallet()));

                                            // response.setRegulationSettings(new RGSettings());
                                            response.setTags(playerInfo.getTags());
                                            // response.setTotalBalance(wallet.getTotalAvailable().getAmount());

                                            gameSession.setAggregateCredits(!playerInfo.isSupportsMultiCredits());
                                            if (!gameSession.getToken().equals(playerInfo.getExternalToken())) {
                                                // gameSession.setPlayer(playerInfo.getUid());
                                                // gameSession.setToken(playerInfo.getExternalToken());

                                                return gameSessionService.findOneByToken(playerInfo.getExternalToken())
                                                        .switchIfEmpty(gameSessionService.createGameSession(gameSession,
                                                                request.getGameId(), playerInfo.getUid(),
                                                                response.getCurrency(), playerInfo.getExternalToken()))
                                                        .map(newSession -> {
                                                            response.setExternalToken(newSession.getUid());
                                                            return response;
                                                        });
                                            } else {
                                                return gameSessionService
                                                        .updatePartial(gameSession.getUid(), Map.of("player",
                                                                playerInfo.getUid(), "currency", response.getCurrency(),
                                                                "aggregateCredits", gameSession.isAggregateCredits()))
                                                        .map(gameSession1 -> {
                                                            response.setExternalToken(sessionToken);
                                                            return response;
                                                        });

                                            }
                                        })
                                        .contextWrite(context -> context.putAllMap(Map.of("TENANT", "default",
                                                "PAM_CONNECTOR", gameSession.getPamConnector())));
                            });
                })
                .tap(() -> new DefaultSignalListener<>() {
                    @Override
                    public void doOnSubscription() throws Throwable {
                        log.info("token received {} ", sessionToken);
                    }
                })
                .contextWrite(context -> context.putAllMap(Map.of("TENANT", "default")));

    }

    @Override
    public Mono<Wallet> playerBalance(PlayerBalanceRequest request) {

        return gameSessionService.findOneByUid(request.getToken())
                .flatMap(gameSession -> {
                    return getPlayerWallet(gameSession).map(PlayerWalletUtils::asWallet)
                            .contextWrite(context -> context.putAllMap(
                                    Map.of("TENANT", "default", "PAM_CONNECTOR", gameSession.getPamConnector())));
                })
                .contextWrite(context -> context.putAllMap(Map.of("TENANT", "default")));

    }

    @Override
    public Mono<PlayerTransactionResponse> rollback(PlayerTransactionRequest request) {
        return null;
    }

    private Mono<PlayerTransactionResponse> handleRollbackGameRound(PlayerTransactionRequest txn, GameRound gameRound,
            GameSession gameSession, Player player) {
        log.info("handle game round rollback. game round status {}", gameRound.getStatus());
        // rollback debit or credit
        if (gameRound.getStatus() != Status.CANCELLED) {
            return gameRoundService.rollback(gameSession, gameRound, player).map(objects -> {
                // Optional player wallet
                PlayerTransactionResponse response = new PlayerTransactionResponse();
                readLastTransaction(objects.getT1(), response);
                return response;
            });
        } else {
            log.info("transaction ignored with txnType {} gameRound {}", txn.getRequestType(), txn.getGameRoundId());
            return getPlayerWallet(gameSession).map(playerWallet -> {
                PlayerTransactionResponse response = new PlayerTransactionResponse();
                response.setWallet(PlayerWalletUtils.asWallet(playerWallet));
                readLastTransaction(gameRound, response);

                return response;
            });
        }
    }

    private static Boolean hasGameSessionExpired(PlayerTransactionRequest txn, GameRound gameRound,
            GameSession gameSession) {
        if (txn.getRequestType() == TransactionType.DEBIT) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.HOUR, -24);// TODO 24 hrs
            if (gameRound.getStatus() == Status.COMPLETED || gameRound.getStatus() == Status.CANCELLED
                    || gameRound.getStatus() == Status.ERROR
                    || gameRound.getModifiedOn().compareTo(calendar.getTime()) < 0) {
                throw new BaseRuntimeException(new DefaultErrorCode("round.closed", "round.closed", 400));
            }

            return gameSession.getModifiedOn().compareTo(calendar.getTime()) < 0;
        }
        return false;
    }

    public Mono<PlayerWallet> getPlayerWallet(GameSession gameSession) {
        return playerService.findOneByUid(gameSession.getPlayer())
                .flatMap(player -> playerService.getBalance(gameSession, player.getCorrelationId()))
                .contextWrite(context -> context.put("PAM_CONNECTOR", gameSession.getPamConnector()));
    }

    private static void readLastTransaction(GameRound updatedGameRound, PlayerTransactionResponse response) {
        int tns = updatedGameRound.getTransactions().size();
        if (tns >= 1)
            response.setTxnId(updatedGameRound.getTransactions().get(tns - 1));
    }

    private Mono<PlayerTransactionResponse> updateGameRound(PlayerTransactionResponse playerTransactionResponse,
            GameRound gameRound, GameSession gameSession) {
        // if (txn.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED)
        // gameRound.setStatus(Status.COMPLETED);
        return gameRoundService.updatePartial(gameRound.getUid(), Map.of("status", gameRound.getStatus()))
                // .tap(() -> new DefaultSignalListener<>() {
                // @Override
                // public void doOnNext(GameRound gameRound) throws Throwable {
                // // log.info("transaction ignored with txnType {} gameRound {}",
                // txn.getRequestType(), gameRound.getCorrelationId());
                // }
                // })
                .flatMap(updatedGameRound -> {

                    if (playerTransactionResponse.getWallet() != null) {
                        PlayerTransactionResponse response = new PlayerTransactionResponse();
                        response.setWallet(playerTransactionResponse.getWallet());
                        readLastTransaction(updatedGameRound, response);

                        return Mono.just(response);
                    } else {
                        return getPlayerWallet(gameSession)
                                .map(playerWallet -> {
                                    PlayerTransactionResponse response = new PlayerTransactionResponse();
                                    response.setWallet(PlayerWalletUtils.asWallet(playerWallet));
                                    readLastTransaction(updatedGameRound, response);

                                    return response;
                                });
                    }

                });
    }

    private Mono<PlayerTransactionResponse> handleNewGameRound(PlayerTransactionRequest txn, GameSession gameSession) {
        return playerService.findOneByUid(gameSession.getPlayer()).flatMap(player -> {

            Mono<GameRound> roundMono = Mono.defer(() -> {
                return gameRoundService.createGameRound(txn.getGameRoundId(), gameSession, txn.getGameId(),
                        "SLOTS", player, null, txn.getTxnId(),
                        txn.getDebit(), txn.getCredit(), txn.getRequestType(),
                        Status.valueOf(txn.getGameRoundStatus().name()),
                        txn.getFreeSpins());
            });

            if (txn.getFreeSpins() != null && txn.getFreeSpins().getCampaignUid() != null) {
                // Fix me. validate the promotion id claimed by player or not.
                // freeSpinsAllotmentService.findByPlayerAndPromotion()
                roundMono = promotionService.findOne(txn.getFreeSpins().getCampaignUid())
                        .doOnNext(promotion -> {
                            txn.getFreeSpins().setCampaignUid(promotion.getPromotionRefId());
                        }).switchIfEmpty(Mono.error(new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST,
                                "Promotion " + txn.getFreeSpins().getCampaignUid() + " not found ")))
                        .then(roundMono);
            }

            return roundMono.flatMap(gameRound -> {

                return currencyService.getCurrency(gameSession.getCurrency())
                        .flatMap(currencyUnit -> {
                            return transactionService.processGameTransaction(
                                    player,
                                    gameRound,
                                    null,
                                    gameSession,
                                    Money.of(txn.getDebit() != null ? txn.getDebit() : 0D,
                                            currencyUnit),
                                    Money.of(txn.getCredit() != null ? txn.getCredit() : 0D,
                                            currencyUnit),
                                    txn.getRequestType(),
                                    txn.getTxnId(),
                                    null, null, true, txn.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED)
                                    .tap(() -> new DefaultSignalListener<Transaction>() {
                                        @Override
                                        public void doOnNext(Transaction value) throws Throwable {
                                            log.info("game-round created with transaction {}", value);
                                        }
                                    })
                                    .flatMap(transaction -> {
                                        return gameRoundService
                                                .updatePartial(gameRound.getUid(),
                                                        Map.of("status", gameRound.getStatus()))
                                                .map((GameRound gameRound1) -> {
                                                    PlayerTransactionResponse response = new PlayerTransactionResponse();
                                                    response.setWallet(
                                                            PlayerWalletUtils.asWallet(transaction.getWallet()));
                                                    response.getProcessedTxnIds().put(transaction.getCorrelationId(),
                                                            transaction.getUid());
                                                    return response;
                                                });
                                    });
                        })
                        .onErrorResume(throwable -> {
                            return gameRoundService.updatePartial(gameRound.getUid(), Map.of("status", Status.ERROR))
                                    .then(Mono.error(throwable));
                        });
            });
        })
                .contextWrite(context -> context.put("PAM_CONNECTOR", gameSession.getPamConnector()));
    }

}
