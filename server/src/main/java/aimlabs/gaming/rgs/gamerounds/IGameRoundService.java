package aimlabs.gaming.rgs.gamerounds;

import aimlabs.gaming.rgs.core.IEntityService;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.players.Player;
import aimlabs.gaming.rgs.players.PlayerWallet;
import aimlabs.gaming.rgs.promotions.FreeSpinCampaign;
import aimlabs.gaming.rgs.transactions.TransactionType;
import org.springframework.data.util.Pair;


import javax.money.MonetaryAmount;
import java.util.Optional;
import java.util.function.Function;

public interface IGameRoundService extends IEntityService<GameRound> {
    //Mono<GameRound> createGameRound(String uid, String extTxnId, GameSession gameSession, String game, String gameType, Player player, String gamePlay, BigDecimal totalWager, String activityUid, boolean autoPlayable, GamePlayResponse gameEngineResponse, boolean confirmHandSupported);
    GameRound createGameRound(String externalId, GameSession gameSession, String game, String gameType, Player player, String gamePlay, String txnId, Double debit, Double credit, TransactionType transactionType, Status gameRoundStatus, FreeSpinCampaign freeSpinCampaign);
    Pair<GameRound, Optional<PlayerWallet>> rollback(GameSession gameSession, GameRound rollBackRound, Player player);
    UnfinishedGame getUnfinishedGames(GameSession gameSession, GameSkin gameSkin, boolean confirmHand);
    GameRound findOneByTenantAndGameIdAndCorrelationId(String tenant, String gameId, String correlationId);
    GameRound createdExternalRollbackGameRound(String extGameRoundId, String extTransactionId, TransactionType txnType, GameSession gameSession, MonetaryAmount amount, GameSkin gameSkin);
    GameRound confirmHand(String gameRound);
    GameRound findLastRoundPendingForGamePlay(String tenant, Object uid);
    GameRound findByGameActivity(String gameActivity);
    GameRound findOneByProviderAndCorrelationId(String provider, String gameRoundId);
    UnfinishedGame getLastGameRoundWithDetails(String playerId, String gameId, String tenant, String currency);
    Boolean isUnfinishedGameRoundExists(String player, String gameId);
}