package aimlabs.gaming.rgs.transactions;

import aimlabs.gaming.rgs.core.IEntityService;
import aimlabs.gaming.rgs.gameoperators.PlayerTransactionRequest;
import aimlabs.gaming.rgs.gameoperators.PlayerTransactionResponse;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.players.Player;

import javax.money.MonetaryAmount;
import java.util.List;

public interface ITransactionService extends IEntityService<Transaction> {

    PlayerTransactionResponse handleTransaction(PlayerTransactionRequest txn, String correlationTxnId, GameRound gameRound, GameSession gameSession, Player player, boolean rollbackRequired, boolean gameRoundCompleted);
    Transaction processGameTransaction(String externalPlayerCorrelationId, GameRound gameRound, String gameActivity, GameSession gameSession, MonetaryAmount debit, MonetaryAmount credit, TransactionType txnType, String extTxnId, String orgTxnId, MonetaryAmount rollbackAmount, boolean rollbackRequired, boolean gameRoundCompleted);
    Transaction getLastPlayerTransaction(GameRound gameRound);
    List<Transaction> findAllTransactions(String gamePlay);
    Transaction findOneByCorrelationId(String tenant, String txnId);
    Transaction findLastPendingTransactionByGameActivity(String gameActivity);
    Transaction findOneByProviderAndCorrelationId(String provider, String gameRoundId);
    List<Transaction> findByGameRound(String gameRoundUid);
    Transaction findByGameRoundAndTxnType(String gameRoundUid, TransactionType txnType);
    Transaction findByGameRoundAndCorrelationId(String gameRound, String txnId);
}