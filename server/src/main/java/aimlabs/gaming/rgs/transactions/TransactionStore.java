package aimlabs.gaming.rgs.transactions;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.gamerounds.GameRoundStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Data
@Slf4j
@Service
public class TransactionStore extends MongoEntityStore<TransactionDocument> {

    @Autowired
    private GameRoundStore gameRoundStore;


    @Autowired
    private TransactionMapper mapper;


    public List<Transaction> findByGamePlay(String gamePlay) {
        return getTemplate().find(Query.query(Criteria.where("gamePlay").is(gamePlay).and("deleted").is(false)).with(Sort.by(Sort.Direction.ASC,"id")), TransactionDocument.class)
                .stream().map(transactionDocument -> mapper.asDto(transactionDocument)).toList();
    }

    public Transaction findOneByCorrelationId(String tenant, String txnId) {
        return getTemplate().findOne(Query.query(Criteria.where("tenant").is(tenant).and("correlationId").is(txnId).and("deleted").is(false)),
                Transaction.class,"Transactions");
    }

    public List<Transaction> findAllPendingCredits(String tenant, String gameRound) {
        return getTemplate().find(Query.query(Criteria.where("tenant").is(tenant).and("gameRound").is(gameRound)
                        .and("txnType").is(TransactionType.CREDIT)
                        .and("status").is(Status.PENDING)
                        .and("deleted").is(false)),
                Transaction.class,"Transactions");
    }
}