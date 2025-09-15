package aimlabs.gaming.rgs.transactions;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import aimlabs.gaming.rgs.gameoperators.PlayerTransactionRequest;
import aimlabs.gaming.rgs.players.PlayerWallet;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.money.MonetaryAmount;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class Transaction extends BaseDto {

    private String uid;

    private String session;

    private String brand;

    private String gameRound;

    private String gameId;

    private String gameConfiguration;

    private String gamePlay;

    private String gameActivity;

    private String player;

    private MonetaryAmount debit;

    private MonetaryAmount credit;

    private String currency;

    private TransactionType txnType;

    private PlayerWallet wallet;

    private Map<String, Object> processedTransactions;

    private String rollbackTxnId;

    private boolean autoPlayed;

    private boolean demo;

    private boolean rollbackRequired;

}
