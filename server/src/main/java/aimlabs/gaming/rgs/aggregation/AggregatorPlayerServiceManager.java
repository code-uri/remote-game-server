package aimlabs.gaming.rgs.aggregation;

import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.connectors.IConnectorService;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.currency.ICurrencyService;
import aimlabs.gaming.rgs.gameoperators.*;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.gamerounds.GameRoundStatusEnum;
import aimlabs.gaming.rgs.gamerounds.IGameRoundService;
import aimlabs.gaming.rgs.games.GameSupplierLocator;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gamesessions.GameSessionContext;
import aimlabs.gaming.rgs.gamesessions.IGameSessionService;
import aimlabs.gaming.rgs.gameskins.IGameSkinService;
import aimlabs.gaming.rgs.players.*;
import aimlabs.gaming.rgs.transactions.ITransactionService;
import aimlabs.gaming.rgs.transactions.Transaction;
import aimlabs.gaming.rgs.transactions.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import javax.money.CurrencyUnit;
import java.lang.ScopedValue;
import java.util.Map;
import java.util.Optional;

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

   /* @Autowired
    BrandGameService brandGameService;*/
/*

    @Autowired
    GameProviderService gameProviderService;
*/


//    @Autowired
//    FreeSpinsAllotmentService freeSpinsAllotmentService;

    @Autowired
    IConnectorService connectorService;

    @Autowired
    GameSupplierLocator gameSupplierLocator;

    @Autowired
    ICurrencyService currencyService;


//    @Autowired
//    IPromotionService promotionService;

    @Override
    public PlayerTransactionResponse playerTransaction(PlayerTransactionRequest request) {


        GameRound gameRound = gameRoundService.findOneByTenantAndGameIdAndCorrelationId("default",
                request.getGameId(), request.getGameRoundId());

        PlayerTransactionResponse playerTransactionResponse;
        if (request.getRequestType() == TransactionType.CREDIT
            || request.getRequestType() == TransactionType.ROLLBACK
            || request.getRequestType() == TransactionType.CLOSED) {
            //handle other transactions excluding DEBIT and DEBIT_CREDIT. rollback not required.
            //no token validation of token. get token from gameRound

            if (gameRound == null)
                throw new BaseRuntimeException(SystemErrorCode.INVALID_GAME_ROUND);

            Player player = playerService.findOneByUid(gameRound.getPlayer());
            GameSession gameSession = gameSessionService.findOneByUid(request.getToken());

            if (gameSession == null) {
                throw new BaseRuntimeException(SystemErrorCode.TOKEN_INVALID);
            }

            GameSession currentGameSession = gameSession;
            try {
                playerTransactionResponse = ScopedValue.where(GameSessionContext.GAME_SESSION, currentGameSession)
                        .call(() -> processTransaction(request, gameRound, currentGameSession, player, false));
            } catch (Exception e) {
                gameRoundService.updatePartial(gameRound.getUid(), Map.of("status", Status.ERROR));
                throw e;
            }
        } else {
            //handle DEBIT and DEBIT_CREDIT request.
            //validate token case.
            GameSession gameSession = gameSessionService.findOneByUid(request.getToken());

            if (gameSession == null)
                throw new BaseRuntimeException(SystemErrorCode.TOKEN_INVALID);

            GameSession currentGameSession = gameSession;

            if (gameRound == null) {
                playerTransactionResponse = ScopedValue.where(GameSessionContext.GAME_SESSION, currentGameSession)
                        .call(() -> handleNewGameRound(request, currentGameSession));
            } else {
                Player player = playerService.findOneByUid(gameRound.getPlayer());

                playerTransactionResponse = ScopedValue.where(GameSessionContext.GAME_SESSION, currentGameSession)
                        .call(() -> processTransaction(request, gameRound, currentGameSession, player, false));

            }
        }
        return playerTransactionResponse;
    }

    private PlayerTransactionResponse processTransaction(PlayerTransactionRequest txn,
                                                         GameRound gameRound,
                                                         GameSession gameSession,
                                                         Player player,
                                                         boolean rollbackRequired) {

        PlayerTransactionResponse playerTransactionResponse = null;
        if (txn.getRequestType() == TransactionType.ROLLBACK) {
            playerTransactionResponse = handleRollbackGameRound(txn, gameRound, gameSession, player);
        }
        else {
            try {

                playerTransactionResponse = transactionService.handleTransaction(txn, txn.getTxnId(), gameRound, gameSession, player, rollbackRequired,
                        (txn.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED || txn.getGameRoundStatus() == GameRoundStatusEnum.CLOSED));
            } catch (BaseRuntimeException err) {

                if(err.getErrorCode()==SystemErrorCode.ROLLBACK_GAME_ROUND){
                    playerTransactionResponse = rollback(txn);

                    throw new BaseRuntimeException(SystemErrorCode.GAME_ROUND_CANCELLED);
                }

            }
        }

        if (gameRound.getStatus()== Status.INPROGRESS && txn.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED) {
            gameRound.setStatus(Status.COMPLETED);
        }
        playerTransactionResponse = updateGameRound(playerTransactionResponse, gameRound, gameSession);

        return playerTransactionResponse;
    }

    @Override
    public PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request) {
        //log.info("request received");
        //log.info("session {} headers {}", webExchange.getRequest().getQueryParams(), webExchange.getRequest().getHeaders() );
        if (request.getSessionToken() == null) {
            throw new BaseRuntimeException(SystemErrorCode.TOKEN_INVALID);
        }

        String sessionToken = request.getSessionToken();
        GameSession initialGameSession = gameSessionService.findOneByUid(sessionToken);

        if(initialGameSession==null)
            throw new BaseRuntimeException(SystemErrorCode.INVALID_TOKEN);

        return ScopedValue.where(GameSessionContext.GAME_SESSION, initialGameSession).call(() -> {
            GameSession gameSession = initialGameSession;

            Player player = playerService.findOneByUid(gameSession.getPlayer());

            PlayerInfo playerInfo = playerService.initialise(player.getNetwork(),
                    gameSession.getToken(),
                    player.getCorrelationId(),
                    gameSession.getCurrency(),
                    gameSession.getBrand(),
                    gameSession.getGame(),
                    true);

            PlayerInitialiseResponse response = new PlayerInitialiseResponse();
            response.setPlayerId(playerInfo.getUid());
            response.unWrapWallet(PlayerWalletUtils.asWallet(playerInfo.getWallet()));

            //response.setRegulationSettings(new RGSettings());
            response.setTags(playerInfo.getTags());
            //response.setTotalBalance(wallet.getTotalAvailable().getAmount());

            gameSession.setAggregateCredits(!playerInfo.isSupportsMultiCredits());
            if(!gameSession.getToken().equals(playerInfo.getExternalToken())) {
//                                                gameSession.setPlayer(playerInfo.getUid());
//                                                gameSession.setToken(playerInfo.getExternalToken());

                gameSession = gameSessionService.findOneByToken(playerInfo.getExternalToken());

                if(gameSession==null){
                    gameSession = gameSessionService.createGameSession(gameSession, request.getGameId(), playerInfo.getUid(), response.getCurrency(), playerInfo.getExternalToken() );
                }


                response.setExternalToken(gameSession.getUid());
                return response;
            }
            else{
                gameSession =  gameSessionService.updatePartial(gameSession.getUid(), Map.of("player", playerInfo.getUid(), "currency", response.getCurrency(),
                        "aggregateCredits", gameSession.isAggregateCredits()));


                response.setExternalToken(sessionToken);
                return response;

            }
        });

    }

    @Override
    public Wallet playerBalance(PlayerBalanceRequest request) {

        GameSession gameSession = gameSessionService.findOneByUid(request.getToken());

        return PlayerWalletUtils.asWallet(getPlayerWallet(gameSession));
    }

    @Override
    public PlayerTransactionResponse rollback(PlayerTransactionRequest request) {
        return null;
    }


    private PlayerTransactionResponse handleRollbackGameRound(PlayerTransactionRequest txn, GameRound gameRound, GameSession gameSession, Player player) {
        log.info("handle game round rollback. game round status {}", gameRound.getStatus());
        //rollback debit or credit
        if (gameRound.getStatus() != Status.CANCELLED){
            Pair<GameRound, Optional<PlayerWallet>> pair = gameRoundService.rollback(gameSession, gameRound, player);

            //Optional player wallet
            PlayerTransactionResponse response = new PlayerTransactionResponse();
            readLastTransaction(pair.getFirst(), response);
            return response;

        }
        else {
            log.info("transaction ignored with txnType {} gameRound {}", txn.getRequestType(), txn.getGameRoundId());
            PlayerWallet playerWallet = getPlayerWallet(gameSession);

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            response.setWallet(PlayerWalletUtils.asWallet(playerWallet));
            readLastTransaction(gameRound, response);

            return response;
        }
    }

    public PlayerWallet getPlayerWallet(GameSession gameSession) {
        Player player = playerService.findOneByUid(gameSession.getPlayer());
        return  playerService.getBalance(gameSession, player.getCorrelationId());
    }


    private static void readLastTransaction(GameRound updatedGameRound,
                                            PlayerTransactionResponse response) {
        int tns = updatedGameRound.getTransactions().size();
        if (tns >= 1) response.setTxnId(updatedGameRound.getTransactions().get(tns - 1));
    }

    private PlayerTransactionResponse updateGameRound(PlayerTransactionResponse playerTransactionResponse,
                                                      GameRound gameRound,
                                                      GameSession gameSession) {
        gameRound = gameRoundService.updatePartial(gameRound.getUid(),
                Map.of("status", gameRound.getStatus()));
        if(playerTransactionResponse.getWallet()!=null){
            PlayerTransactionResponse response = new PlayerTransactionResponse();
            response.setWallet(playerTransactionResponse.getWallet());
            readLastTransaction(gameRound, response);

            return response;
        }else{
            PlayerWallet playerWallet = getPlayerWallet(gameSession);

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            response.setWallet(PlayerWalletUtils.asWallet(playerWallet));
            readLastTransaction(gameRound, response);

            return response;
        }
    }


    private PlayerTransactionResponse handleNewGameRound(PlayerTransactionRequest txn, GameSession gameSession) {
        Player player = playerService.findOneByUid(gameSession.getPlayer());
        GameRound gameRound;

        if (txn.getFreeSpins() != null && txn.getFreeSpins().getCampaignUid() != null) {
            //Fix me. validate the promotion id claimed by player or not.
            //freeSpinsAllotmentService.findByPlayerAndPromotion()
//            gameRound = promotionService.findOne(txn.getFreeSpins().getCampaignUid())
//                    .doOnNext(promotion -> {
//                        txn.getFreeSpins().setCampaignUid(promotion.getPromotionRefId());
//                    }).switchIfEmpty(Mono.error(new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Promotion " + txn.getFreeSpins().getCampaignUid() + " not found ")))


            gameRound = gameRoundService.createGameRound(txn.getGameRoundId(), gameSession, txn.getGameId(),
                    "SLOTS", player, null, txn.getTxnId(),
                    txn.getDebit(), txn.getCredit(), txn.getRequestType(),
                    Status.valueOf(txn.getGameRoundStatus().name()),
                    txn.getFreeSpins());
            log.info("Existing Game round {} ", gameRound.getUid());
        }else{
            gameRound = gameRoundService.createGameRound(txn.getGameRoundId(), gameSession, txn.getGameId(),
                    "SLOTS", player, null, txn.getTxnId(),
                    txn.getDebit(), txn.getCredit(), txn.getRequestType(),
                    Status.valueOf(txn.getGameRoundStatus().name()),
                    txn.getFreeSpins());
            log.info("Game round created {} ", gameRound);
        }



        CurrencyUnit currencyUnit = currencyService.getCurrency(gameSession.getCurrency());

        Transaction transaction = transactionService.processGameTransaction(
                player.getCorrelationId(),
                gameRound,
                null,
                gameSession,
                Money.of(txn.getDebit() != null ? txn.getDebit() : 0D,
                        currencyUnit),
                Money.of(txn.getCredit() != null ? txn.getCredit() : 0D,
                        currencyUnit),
                txn.getRequestType(),
                txn.getTxnId(),
                null, null, true, txn.getGameRoundStatus() == GameRoundStatusEnum.COMPLETED);


        try{
            gameRound =  gameRoundService.updatePartial(gameRound.getUid(), Map.of("status", gameRound.getStatus()));

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            response.setWallet(PlayerWalletUtils.asWallet(transaction.getWallet()));
            response.getProcessedTxnIds().put(transaction.getCorrelationId(), transaction.getUid());
            return response;

        } catch (Exception e) {
            gameRound =  gameRoundService.updatePartial(gameRound.getUid(), Map.of("status", Status.ERROR));

            throw  e;
        }
    }

}

