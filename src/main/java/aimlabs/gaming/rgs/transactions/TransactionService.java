package aimlabs.gaming.rgs.transactions;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.currency.ICurrencyService;
import aimlabs.gaming.rgs.gameoperators.PlayerAccountManager;
import aimlabs.gaming.rgs.gameoperators.PlayerTransactionRequest;
import aimlabs.gaming.rgs.gameoperators.PlayerTransactionResponse;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.gamerounds.GameRoundStatusEnum;
import aimlabs.gaming.rgs.gamerounds.GameRoundStore;
import aimlabs.gaming.rgs.games.TenantContextHolder;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.players.IPlayerService;
import aimlabs.gaming.rgs.players.Player;
import aimlabs.gaming.rgs.players.PlayerWalletUtils;
import aimlabs.gaming.rgs.promotions.FreeSpinCampaign;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Getter
@Setter
@Component
public class TransactionService extends AbstractEntityService<Transaction, TransactionDocument> implements ITransactionService {

    @Autowired
    GameRoundStore gameRoundStore;
    @Autowired
    IPlayerService playerService;
    @Autowired
    ICurrencyService currencyService;
    @Autowired
    private TransactionStore store;
    @Autowired
    private TransactionMapper mapper;
    @Autowired
    private PlayerAccountManager playerAccountManager;
    @Autowired
    private ObjectMapper objectMapper;

    private static void readLastTransaction(GameRound updatedGameRound,
                                            PlayerTransactionResponse response) {
        int tns = updatedGameRound.getTransactions().size();
        if (tns >= 1) response.setTxnId(updatedGameRound.getTransactions().get(tns - 1));
    }

    public PlayerTransactionResponse handleTransaction(PlayerTransactionRequest txn,
                                                       String correlationTxnId,
                                                       GameRound gameRound,
                                                       GameSession gameSession,
                                                       Player player,
                                                       boolean rollbackRequired,
                                                       boolean gameRoundCompleted) {
        CurrencyUnit currencyUnit = currencyService.getCurrency(gameSession.getCurrency());


        Money debit = Money.of(0, currencyUnit);
        Money credit = Money.of(0, currencyUnit);


        if (txn.getDebit() != null) {
            debit = Money.of(txn.getDebit(), currencyUnit);
        }

        if (txn.getCredit() != null) {
            credit = Money.of(txn.getCredit(), currencyUnit);
        }

        PlayerTransactionResponse response = new PlayerTransactionResponse();

        //rollback debit or credit
        Transaction transaction = processGameTransaction(player.getCorrelationId(),
                gameRound, null
                , gameSession, debit, credit, txn.getRequestType(), txn.getTxnId(), correlationTxnId, null, rollbackRequired, gameRoundCompleted);


        if (transaction.getCorrelationId() != null)
            response.getProcessedTxnIds().put(transaction.getCorrelationId(), transaction.getUid());

        if (transaction.getWallet() != null)
            response.setWallet(PlayerWalletUtils.asWallet(transaction.getWallet()));

        return response;
    }

    @Override
    public Transaction afterUpdate(Transaction entity) {
        Transaction transaction = super.afterUpdate(entity);
        Transaction gameRoundUpdate = gameRoundStore.updateTotalWinAndWallet(transaction.getGameRound(), transaction);

        playerService.updateBalance(transaction.getPlayer(), transaction.getWallet());

        return transaction;

    }

    public Transaction processGameTransaction(String externalPlayerCorrelationId,
                                              GameRound gameRound,
                                              String gameActivity,
                                              GameSession gameSession,
                                              MonetaryAmount debit,
                                              MonetaryAmount credit,
                                              TransactionType txnType,
                                              String extTxnId,
                                              String orgTxnId,
                                              MonetaryAmount rollbackAmount,
                                              boolean rollbackRequired,
                                              boolean gameRoundCompleted
    ) {

        if (extTxnId != null)
            return findOneByCorrelationId(gameSession.getTenant(), extTxnId);


        CurrencyUnit currencyUnit = currencyService.getCurrency(gameSession.getCurrency());

        if (gameRound.getStatus() != Status.INPROGRESS && gameRound.getStatus() != Status.COMPLETED && gameRound.getStatus() != Status.CLOSED) {
            throw new BaseRuntimeException(SystemErrorCode.INVALID_GAME_ROUND, "invalid game round status " + gameRound.getStatus());
        }

        Transaction playerTransaction = new Transaction();
        playerTransaction.setUid(UUID.randomUUID().toString());
        playerTransaction.setGameRound(gameRound.getUid());
        playerTransaction.setSession(gameSession.getUid());
        playerTransaction.setPlayer(gameSession.getPlayer());
        playerTransaction.setBrand(gameSession.getBrand());
        playerTransaction.setGameId(gameRound.getGameId());
        playerTransaction.setGameConfiguration(gameRound.getGameConfiguration());
        playerTransaction.setGameActivity(gameActivity);
        playerTransaction.setDemo(gameSession.isDemo());
        playerTransaction.setGamePlay(gameRound.getGamePlay());
        playerTransaction.setRollbackRequired(rollbackRequired);

        MonetaryAmount zero = Money.ofMinor(currencyUnit, 0);
        playerTransaction.setCredit(zero);
        playerTransaction.setDebit(zero);
        playerTransaction.setCurrency(gameSession.getCurrency());

        if (txnType == TransactionType.CREDIT) {
            playerTransaction.setCredit(credit);
            // playerTransaction.setJackpotDetails(gameRound.getJackpotDetails());
        } else if (txnType == TransactionType.DEBIT)
            playerTransaction.setDebit(debit);
        else if (txnType == TransactionType.DEBIT_CREDIT) {
            playerTransaction.setDebit(debit);
            playerTransaction.setCredit(credit);
        }

        playerTransaction.setTxnType(txnType);

        if (txnType == TransactionType.CREDIT && gameSession.isAggregateCredits() && !gameRoundCompleted)
            playerTransaction.setStatus(Status.PENDING);
        else
            playerTransaction.setStatus(Status.INPROGRESS);

        playerTransaction.setCorrelationId(extTxnId);


        playerTransaction.setAutoPlayed(false);
        Transaction transaction = create(playerTransaction);

        if (transaction.getStatus() == Status.COMPLETED ||
            (!gameRoundCompleted && gameSession.isAggregateCredits() && transaction.getStatus() == Status.PENDING))
            return transaction;

        return processTransaction(gameSession, gameRound, externalPlayerCorrelationId, txnType, transaction, orgTxnId, rollbackAmount, gameRoundCompleted);
    }

   /* private void cleanUpTransaction(GameSession gameSession, GameRound gameRound, Player player, Transaction transaction, SignalType signalType) {
        log.warn("cleanUpTransaction transaction {} failed with signalType {}", transaction.getUid(), signalType);
        store.findOneByUid(transaction.getUid())
                .filter(playerTransactionDocument -> playerTransactionDocument.getStatus() != Status.COMPLETED)
                .flatMap(playerTransactionDocument -> {
                    return handlerError(gameSession,
                            gameRound,
                            player,
                            transaction,
                            new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "Reactor subscription " + signalType));
                })
                .subscribe();
    }
*/

    private Transaction processTransaction(GameSession gameSession, GameRound gameRound,
                                           String externalPlayerCorrelationId, TransactionType txnType,
                                           Transaction transaction, String orgTxnId,
                                           MonetaryAmount rollbackAmount,
                                           boolean gameRoundCompleted) {


        try {
            PlayerTransactionRequest request = new PlayerTransactionRequest();
            request.setTxnId(transaction.getUid());
            request.setToken(gameSession.getToken());
            request.setInternalToken(gameSession.getUid());
            request.setRequestType(txnType);
            request.setCurrency(transaction.getCurrency());
            request.setGameId(gameRound.getGameId());
            request.setPlayerId(externalPlayerCorrelationId);
            request.setTenant(transaction.getTenant());

            if (txnType == TransactionType.CREDIT) {

                request.setTxnId(transaction.getUid());
                request.setCredit(transaction.getCredit().getNumber().doubleValue());
            } else if (txnType == TransactionType.DEBIT) {
                request.setTxnId(transaction.getUid());
                request.setDebit(transaction.getDebit().getNumber().doubleValue());
            } else if (txnType == TransactionType.DEBIT_CREDIT) {
                request.setTxnId(transaction.getUid());
                request.setDebit(transaction.getDebit().getNumber().doubleValue());
                request.setCredit(transaction.getCredit().getNumber().doubleValue());
            } else if (txnType == TransactionType.CLOSED) {
                request.setTxnId(transaction.getUid());
            }

            if (txnType == TransactionType.CREDIT || txnType == TransactionType.DEBIT || txnType == TransactionType.DEBIT_CREDIT || txnType == TransactionType.CLOSED)
                request.setGameRoundStatus(gameRoundCompleted ? GameRoundStatusEnum.COMPLETED : GameRoundStatusEnum.INPROGRESS);


            request.setGameRoundId(gameRound.getUid());
            request.setOrgTxnUid(orgTxnId);
            if (rollbackAmount != null)
                request.setOrgTxnAmount(rollbackAmount.getNumber().doubleValueExact());

        /*if (playerTransaction.getJackpotDetails() != null
            && playerTransaction.getJackpotDetails().getTotalJackpotWinningsInPlayerCurrency() != null
            && playerTransaction.getJackpotDetails().getTotalJackpotWinningsInPlayerCurrency().isPositive())
            request.setJackpotDetails(getObjectMapper().valueToTree(playerTransaction.getJackpotDetails()));*/

            PlayerTransactionResponse txnResponse = null;
            if (txnType == TransactionType.ROLLBACK) {
                txnResponse = playerAccountManager.rollback(request);
            } else {
                FreeSpinCampaign freeSpins = gameRound.getFreeSpins();
                if (freeSpins != null) {
                    request.setFreeSpins(freeSpins);
                }

                if (txnType == TransactionType.CREDIT && gameRoundCompleted && gameSession.isAggregateCredits()) {
                    List<Transaction> txns = getStore().findAllPendingCredits(gameRound.getTenant(), gameRound.getUid());
                    MonetaryAmount monetaryAmount = txns.stream().map(Transaction::getCredit).reduce(Money.of(request.getCredit(), transaction.getCredit().getCurrency()), MonetaryAmount::add);
                    log.info("aggregated credit amount {} ", monetaryAmount.getNumber());
                    request.setCredit(monetaryAmount.getNumber().doubleValue());
                    txnResponse = playerAccountManager.playerTransaction(request);

                    List<String> txnUids = txns.stream().map(Transaction::getUid).toList();
                    Query query = Query.query(Criteria.where("uid").in(txnUids));
                    getStore().getTemplate().updateMulti(query, Update.update("status", "COMPLETED"),
                            TransactionDocument.class);


                } else {
                    txnResponse = playerAccountManager.playerTransaction(request);
                }
            }

            if (txnResponse.getWallet() != null) {
                transaction.setWallet(PlayerWalletUtils.asPlayerWallet(txnResponse.getWallet()));
                transaction.setStatus(Status.COMPLETED);
            } else {
                transaction.setWallet(PlayerWalletUtils.zeroBalance(gameSession.getCurrency()));
            }
            transaction.setProcessedTransactions(txnResponse.getProcessedTxnIds());
            transaction.setRollbackTxnId(txnResponse.getRollbackTxnId());
            return update(transaction);
        } catch (RuntimeException orgException) {
            handlerError(gameSession, gameRound, externalPlayerCorrelationId, transaction, orgException, gameRoundCompleted);
            throw orgException;
        }
    }

    private Transaction handlerError(GameSession gameSession,
                                     GameRound gameRound,
                                     String externalPlayerCorrelationId,
                                     Transaction transaction,
                                     RuntimeException orgException, boolean gameRoundCompleted) {

        if (transaction.isRollbackRequired()) {
            if (orgException instanceof BaseRuntimeException err && SystemErrorCode.ROLLBACK_GAME_ROUND == err.getErrorCode()) {
                Transaction errorTxn = updatePartial(transaction.getUid(), Map.of("status", Status.ERROR));

                Transaction rollBackTransaction = processGameTransaction(externalPlayerCorrelationId,
                        gameRound,
                        null,
                        gameSession,
                        transaction.getDebit(),
                        transaction.getCredit(),
                        TransactionType.ROLLBACK,
                        null,
                        errorTxn.getUid(), errorTxn.getDebit(), false, gameRoundCompleted);


                gameRoundStore.updateStatus(gameRound.getUid(), Status.CANCELLED);

                throw new BaseRuntimeException(SystemErrorCode.GAME_ROUND_CANCELLED, orgException.getCause() != null ? orgException.getCause() : orgException);
            } else {
                return updatePartial(transaction.getUid(), Map.of("status", Status.ERROR));
            }
        }

        updatePartial(transaction.getUid(), Map.of("status", Status.COM_ERROR));

        throw orgException;

    }

    public Transaction getLastPlayerTransaction(GameRound gameRound) {
        if (gameRound.getTransactions().isEmpty())
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR);

        return getMapper().asDto(store.findOneByUid(gameRound.getTransactions()
                .get(gameRound.getTransactions().size() - 1)));
    }

    public List<Transaction> findAllTransactions(String gamePlay) {
        return store.findByGamePlay(gamePlay);
    }


    public Transaction findOneByCorrelationId(String tenant, String txnId) {
        return store.findOneByCorrelationId(tenant, txnId);
    }

    public Transaction findLastPendingTransactionByGameActivity(String gameActivity) {
        return getStore().getTemplate().findOne(Query.query(Criteria.where("gameActivity").is(gameActivity)
                                .and("status").is("INPROGRESS").and("deleted").is(false))
                        .with(Sort.by(Sort.Direction.DESC, "id")).limit(1)
                , Transaction.class, "Transactions");
    }

    public Transaction findOneByProviderAndCorrelationId(String provider, String gameRoundId) {
        return getStore().getTemplate().findOne(Query.query(Criteria.where("provider").is(provider)
                        .and("gameRoundId").is(gameRoundId).and("deleted").is(false))
                , Transaction.class, "Transactions");
    }

    public List<Transaction> findByGameRound(String gameRoundUid) {
        return getStore().getTemplate().find(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                        .and("gameRound").is(gameRoundUid).and("deleted").is(false))
                , Transaction.class, "Transactions");
    }

    public Transaction findByGameRoundAndTxnType(String gameRoundUid, TransactionType txnType) {
        return getStore().getTemplate()
                .findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                                .and("gameRound").is(gameRoundUid)
                                .and("txnType").is(txnType)
                                .and("deleted").is(false))
                        , Transaction.class, "Transactions");
    }

    public Transaction findByGameRoundAndCorrelationId(String gameRound, String txnId) {
        return getStore().getTemplate()
                .findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                                .and("gameRound").is(gameRound).and("correlationId").is(txnId)
                                .and("deleted").is(false))
                        , Transaction.class, "Transactions");
    }
}
