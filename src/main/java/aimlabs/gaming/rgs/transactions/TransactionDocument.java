package aimlabs.gaming.rgs.transactions;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import aimlabs.gaming.rgs.players.PlayerWallet;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.money.MonetaryAmount;
import java.util.Map;

@Data
@Document(collection = "Transactions")
@CompoundIndexes(value = {
        @CompoundIndex(def = "{'tenant': 1,'correlationId': 1}"),
        @CompoundIndex(def = "{'deleted':1, 'tenant': 1,'correlationId': 1}"),
        @CompoundIndex(def = "{ tenant: 1, deleted: 1, createdOn: -1 }", collation = "en", background = true)
})
public class TransactionDocument extends EntityDocument {

    @Indexed(name = "uid_1", unique = true)
    private String uid;

    private String session;

    private String gameRound;

    private String gameId;

    private String gameConfiguration;

    private String player;

    private String brand;

    private MonetaryAmount debit;

    private MonetaryAmount credit;

    private String currency;

    private TransactionType txnType;

    private PlayerWallet wallet;

    private Map<String, Object> processedTransactions;

    private String rollbackTxnId;

    private boolean autoPlayed;

    private String gamePlay;

    private String gameActivity;

    private boolean demo;

    private boolean rollbackRequired;

    //private JackpotDetails jackpotDetails;
}
