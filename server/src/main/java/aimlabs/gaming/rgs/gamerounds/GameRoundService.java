package aimlabs.gaming.rgs.gamerounds;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.currency.ICurrencyService;
import aimlabs.gaming.rgs.gameoperators.PlayerAccountManager;
import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import aimlabs.gaming.rgs.games.GamePlayResponse;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.players.Player;
import aimlabs.gaming.rgs.players.PlayerWallet;
import aimlabs.gaming.rgs.promotions.FreeSpinCampaign;
import aimlabs.gaming.rgs.transactions.Transaction;
import aimlabs.gaming.rgs.transactions.TransactionService;
import aimlabs.gaming.rgs.transactions.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static aimlabs.gaming.rgs.transactions.TransactionType.ROLLBACK;

@Slf4j
@Getter
@Setter
@Component
public class GameRoundService extends AbstractEntityService<GameRound, GameRoundDocument> implements IGameRoundService {

    public static final String _gamePlay = "gamePlay";
    public static final String _gamePlayState = "gamePlayState";
    public static final String _gameStatus = "gameStatus";
    public static final String _wallet = "wallet";
    public static final String GAME_ACTIVITIES = "gameActivities";
    public static final String GAME_ACTIVITY = "gameActivity";
    public static final String _gameRound = "gameRound";
    private static final String _winnings = "win";
    private static final String GAME_ACTIVITY_DEBIT_AMOUNT = "wager";
    @Autowired
    private GameRoundStore store;

    @Autowired
    private GameRoundMapper mapper;

    @Autowired
    private TransactionService gameTransactionService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PlayerAccountManager playerAccountManager;

    @Autowired
    ICurrencyService currencyService;


    public GameRound createGameRound(String uid,
                                     String extTxnId,
                                     GameSession gameSession,
                                     String game,
                                     String gameType,
                                     Player player,
                                     String gamePlay,
                                     BigDecimal totalWager,
                                     String activityUid,
                                     boolean autoPlayable, GamePlayResponse gameEngineResponse,
                                     boolean confirmHandSupported) {

        CurrencyUnit currencyUnit = currencyService.getCurrency(gameSession.getCurrency());

        GameRound gameRound = new GameRound();
        gameRound.setUid(gameEngineResponse.getGameRoundId() != null ? gameEngineResponse.getGameRoundId() : null);
        gameRound.setTenant(gameSession.getTenant());
        gameRound.setGameId(game);
        gameRound.setGameConfiguration(gameSession.getGameConfiguration());
        gameRound.setGameType(gameType);
        gameRound.setGamePlay(gamePlay);
        //gameRound.setSession(gameSession.getUid());
        gameRound.setBrand(gameSession.getBrand());
        gameRound.setDemo(gameSession.isDemo());
        gameRound.setPlayer(gameSession.getPlayer());
        gameRound.setSession(gameSession.getUid());
        gameRound.setAutoPlayable(!gameSession.isDemo() && autoPlayable);
        gameRound.setTotalWager(Money.of(0,
                currencyUnit));
        gameRound.setTotalWin(Money.of(0,
                currencyUnit));


         /*if(gameEngineResponse.getStreakCounter()!=null){
                            gameEngineResponse.getStreakCounter().get()
                    }*/
        if (gameEngineResponse.getFreeSpins() != null)
            gameRound.setFreeSpins(gameEngineResponse.getFreeSpins());
        gameRound.setFreeSpinsAllotmentId(gameEngineResponse.getFreeSpinsAllotmentId());

                        /*if (gameEngineResponse != null) {
                            String gamePlayStatus = gameEngineResponse.getGamePlayStatus();
                            gameRound.setStatus(gameEngineResponse.isRoundCompleted()
                                    ? Status.COMPLETED
                                    : Status.valueOf(gamePlayStatus));
                        } else {*/
        gameRound.setStatus(Status.INPROGRESS);
        //}


        GameRound gameRound1 = create(gameRound);
        gameEngineResponse.setGameRoundId(gameRound1.getUid());
        return gameRound1;
    }


    public GameRound createGameRound(String externalId,
                                     GameSession gameSession,
                                     String game,
                                     String gameType,
                                     Player player,
                                     String gamePlay,
                                     String txnId,
                                     Double debit, Double credit,
                                     TransactionType transactionType,
                                     Status gameRoundStatus,
                                     FreeSpinCampaign freeSpins) {
        CurrencyUnit currencyUnit = currencyService.getCurrency(gameSession.getCurrency());

        GameRound gameRound = new GameRound();

        gameRound.setCorrelationId(externalId);
        gameRound.setTenant(gameSession.getTenant());
        gameRound.setGameId(game);
        gameRound.setGameType(gameType);
        gameRound.setGamePlay(gamePlay);
        gameRound.setGameConfiguration(gameSession.getGameConfiguration());
        gameRound.setBrand(gameSession.getBrand());
        gameRound.setDemo(gameSession.isDemo());
        gameRound.setPlayer(gameSession.getPlayer());
        gameRound.setSession(gameSession.getUid());
        gameRound.setTotalWager(Money.of(0,currencyUnit));
        gameRound.setTotalWin(Money.of(0,
                currencyUnit));


        gameRound.setFreeSpins(freeSpins);
        if(freeSpins!=null)
            gameRound.setPromotionRefId(freeSpins.getCampaignUid());

        gameRound.setStatus(gameRoundStatus);
        return getMapper().asDto(this.store.create(mapper.asEntity(gameRound)));
    }

/*    private Mono<GameRound> updateGameRound(GameRound gameRound) {
        return getStore().updateGameRound(gameRound);
    }*/

   /* public Mono<GameRound> inProgressGameRoundAcquireLock(JsonNode content) {
        if (content.has(_gameRound)) {
            String gameRoundId = content.get(_gameRound).asText();
            return acquireLockOnGameRound(gameRoundId)
                    .flatMap(acquired -> this.store.findOneByUid(gameRoundId)
                            .map(doc -> mapper.asDto(doc))
                            .filter(gameRound -> gameRound.getStatus() == Status.INPROGRESS)
                            .switchIfEmpty(Mono.error(new GameEngineException("GameRound not found."))));
        } else
            return Mono.empty();
    }*/

    public Pair<GameRound, Optional<PlayerWallet>> rollback(GameSession gameSession,
                                                            GameRound rollBackRound,
                                                            Player player) {
        GameRound gameRound = findOneByUid(rollBackRound.getUid());
        if(gameRound==null){
            throw new BaseRuntimeException(SystemErrorCode.INVALID_GAME_ROUND, "Invalid game round " + rollBackRound.getUid());
        }
        Optional<Transaction> rollBackTransactionOptional = Optional.ofNullable(gameTransactionService
                .findByGameRoundAndTxnType(rollBackRound.getUid(), ROLLBACK));


        if(gameRound.getStatus()==Status.CANCELLED){
            return Pair.of(gameRound, Optional.<PlayerWallet>empty());
        }
        Transaction rollBackTransaction = gameTransactionService.processGameTransaction(player.getCorrelationId(),
                gameRound,
                null,
                gameSession,
                gameRound.getTotalWager(),
                null,
                ROLLBACK,
                rollBackTransactionOptional.map(Transaction::getUid).orElse(null),
                !gameRound.getTransactions().isEmpty() ? gameRound.getTransactions().get(0) : null, null, false, false);

        store.updateStatus(rollBackRound.getUid(), Status.CANCELLED);
        return Pair.of(gameRound, Optional.ofNullable(rollBackTransaction.getWallet()));
    }

    public Boolean acquireLockOnGameRound(String gameRound) {

        Boolean acquired = getRedisTemplate()
                .opsForValue().setIfAbsent(gameRound, true, Duration.ofSeconds(10));
        if(Boolean.FALSE.equals(acquired))
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "failed to acquire lock on "+gameRound);

        return true;
    }


    public Boolean acquireLockOnGamePlay(String gamePlay) {

        if (log.isDebugEnabled())
            log.debug("Acquiring lock on gamePlay {}", gamePlay);

        Boolean acquired = getRedisTemplate()
                .opsForValue().setIfAbsent(gamePlay, true, Duration.ofSeconds(10));

        if(Boolean.FALSE.equals(acquired))
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "failed to acquire lock on "+gamePlay);

        return true;
    }

    public GameRound releaseLockOnGameRound(GameRound gameRound) {
        Boolean lockReleased = redisTemplate.delete(gameRound.getUid());
        if (lockReleased) {
            log.info("GameRound {} lock released.", gameRound.getUid());
        }
        return gameRound;
    }

    public Boolean releaseLockOnGamePlay(String gamePlay) {
        Boolean lockReleased = redisTemplate.delete(gamePlay);
        if (lockReleased) {
            log.info("GamePlay {} lock released.", gamePlay);
        }
        return true;
    }


    public void updateStatusAndReturnError(String gameRound, Throwable throwable) {

        log.error("GameRound {} Request failed.", gameRound, throwable);

//            if (throwable instanceof GameEngineException) {
//                throw throwable;
//            }
////            else if(throwable instanceof BaseRuntimeException e && e.getErrorCode() == SystemErrorCode.COM_ERROR){
////                return Mono.error(new BaseException(SystemErrorCode.SYSTEM_ERROR, throwable.getMessage()));
////            }
//            else
        if(throwable instanceof BaseRuntimeException e && e.getErrorCode() == SystemErrorCode.GAME_ROUND_CANCELLED){
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, throwable.getMessage());
        }
//            else if(throwable instanceof BaseRuntimeException e && e.getErrorCode() == SystemErrorCode.COM_ERROR){
//                return Mono.error(new BaseException(SystemErrorCode.SYSTEM_ERROR, throwable.getMessage()));
//            }
        else {
            getStore()
                    .updateStatus(gameRound, Status.ERROR);

            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, throwable.getMessage());
        }
    }


    public UnfinishedGame getUnfinishedGames(GameSession gameSession, GameSkin gameSkin, boolean confirmHand) {
        return getStore().findUnfinishedGames(gameSession.getPlayer(), gameSkin, gameSession.isDemo(), confirmHand);
    }

    public GameRound findOneByTenantAndGameIdAndCorrelationId(String tenant, String gameId, String correlationId) {
        return getStore().findOneByTenantAndGameIdAndCorrelationId(tenant, gameId, correlationId);
    }

    public GameRound createdExternalRollbackGameRound(String extGameRoundId, String extTransactionId,
                                                      TransactionType txnType, GameSession gameSession,
                                                      MonetaryAmount amount, GameSkin gameSkin) {

        CurrencyUnit currencyUnit = currencyService.getCurrency(gameSession.getCurrency());

        GameRound gameRound = new GameRound();
        gameRound.setUid(UUID.randomUUID().toString());
        gameRound.setCorrelationId(extGameRoundId);
        gameRound.setTenant(gameSession.getTenant());
        gameRound.setGameId(gameSkin.getUid());
        gameRound.setGameType(gameSkin.getGameType());
        //gameRound.setSession(gameSession.getUid());
        gameRound.setBrand(gameSession.getBrand());
        gameRound.setDemo(gameSession.isDemo());
        gameRound.setPlayer(gameSession.getPlayer());
        gameRound.setSession(gameSession.getUid());
        gameRound.setTotalWager(Money.of(0,
                currencyUnit));
        gameRound.setTotalWin(Money.of(0,
                currencyUnit));
        gameRound.setStatus(Status.CANCELLED);

        gameRound =  super.create(gameRound);

        Transaction transaction = new Transaction();
        transaction.setUid(UUID.randomUUID().toString());
        transaction.setCorrelationId(extTransactionId);
        transaction.setRollbackTxnId(transaction.getUid());
        transaction.setBrand(gameSession.getBrand());
        transaction.setGameId(gameSkin.getUid());
        transaction.setGameConfiguration(gameRound.getGameConfiguration());
        transaction.setGameRound(gameRound.getUid());
        transaction.setStatus(Status.COMPLETED);
        if (txnType == TransactionType.DEBIT) {
            transaction.setDebit(gameRound.getTotalWager());
            transaction.setTxnType(TransactionType.DEBIT);
        } else {
            transaction.setCredit(gameRound.getTotalWager());
            transaction.setTxnType(TransactionType.CREDIT);
        }

        transaction.setCurrency(gameSession.getCurrency());
        transaction.setDemo(gameSession.isDemo());
        transaction.setSession(gameSession.getUid());

        transaction = gameTransactionService.create(transaction);


        gameRound.getTransactions().add(transaction.getUid());

        return gameRound;
    }

    public GameRound confirmHand(String gameRoundUid) {
        GameRound gameRound = getStore().getTemplate().findAndModify(Query.query(Criteria.where("uid").is(gameRoundUid).and("deleted").is(false)), Update.update("handConfirmed", true),
                FindAndModifyOptions.options().returnNew(true), GameRound.class, "GameRounds");

        if(gameRound==null)
            throw new BaseRuntimeException(SystemErrorCode.INVALID_GAME_ROUND, "Invalid game round " + gameRoundUid);

        return gameRound;
    }

    public GameRound findLastRoundPendingForGamePlay(String tenant, Object uid) {
        return getStore().getTemplate().findOne(Query.query(Criteria.where("gamePlay").is(uid)
                                .and("status").is("INPROGRESS").and("deleted").is(false))
                        .with(Sort.by(Sort.Direction.DESC, "id")).limit(1)
                , GameRound.class, "GameRounds");
    }

    public GameRound findByGameActivity(String gameActivity) {
        Transaction transaction = gameTransactionService.findLastPendingTransactionByGameActivity(gameActivity);

        return getStore().getTemplate().findOne(Query.query(Criteria.where("uid").is(transaction.getGameRound())
                                .and("status").is("INPROGRESS").and("deleted").is(false))
                        .with(Sort.by(Sort.Direction.DESC, "id")).limit(1)
                , GameRound.class, "GameRounds");
    }

    public GameRound findOneByProviderAndCorrelationId(String provider, String gameRoundId) {

        return getStore().getTemplate().findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                        .and("provider").is(provider)
                        .and("gameRoundId").is(gameRoundId).and("deleted").is(false))
                , GameRound.class, "GameRounds");
    }


    public UnfinishedGame getLastGameRoundWithDetails(String playerId, String gameId, String tenant, String currency) {
        return store.getLastGameRoundWithDetails(playerId, gameId, tenant, currency);
    }

    @Override
    public Boolean isUnfinishedGameRoundExists(String player, String gameId) {
        return getStore().getTemplate().exists(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                        .and("player").is(player)
                        .and("gameId").is(gameId).and("status").is(Status.INPROGRESS).and("deleted").is(false))
                , GameRound.class, "GameRounds");
    }
}
