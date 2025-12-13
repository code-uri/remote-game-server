package aimlabs.gaming.rgs.gamesessions;


import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.games.TenantContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Data
@Slf4j
@Service
public class GameSessionStore extends MongoEntityStore<GameSessionDocument> {


/*
    @Autowired
    private GameSessionReactiveRepository repository;
*/


    public GameSessionDocument findOneByUidAndStatus(String uid, Status status) {
        return getTemplate().findOne(Query.query(Criteria.where("uid").is(uid)
                .and("status").is(status).and("deleted").is(false)), GameSessionDocument.class);
        //return getRepository().findOneByUidAndStatus(uid, status);
    }

    public GameSessionDocument findOneByToken(String token) {
        Query query = Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant()).and("token").is(token).and("deleted").is(false));

        return getTemplate().findOne(query.with(Sort.by(Sort.Order.desc("id"))).limit(1), GameSessionDocument.class);
    }

    public GameSessionDocument findLastOneByPlayer(String player) {
        return getTemplate().findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                        .and("player").is(player).and("deleted").is(false))
                .with(Sort.by(Sort.Order.desc("id"))).limit(1), GameSessionDocument.class);

    }

    public GameSessionDocument findOneByTokenAndStatus(String token, Status status){

        Query query = Query.query(Criteria.where("token").is(token).and("status").is(status));
        return getTemplate().findOne(query, GameSessionDocument.class);
    }

    public GameSessionDocument findOneByGameConnectorAndCorrelationIdAndStatus(String gameConnector, String correlationId, Status status) {
        return getTemplate().findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                .and("gameConnector").is(gameConnector).and("correlationId").is(correlationId).and("status").is(status).and("deleted").is(false)), GameSessionDocument.class);
    }

    public GameSessionDocument findOneByTokenAndUpdateGame(String externalToken, String gameId, String gameConfiguration) {

        String tenant = TenantContextHolder.getTenant();
        log.info("find game session for tenant {} token {}", tenant, externalToken);
        Query query = Query.query(Criteria.where("tenant").is(tenant).and("token").is(externalToken).and("deleted").is(false));

        return getTemplate().findAndModify(query, Update.update("game", gameId).set("gameConfiguration", gameConfiguration),
                FindAndModifyOptions.options().upsert(false).returnNew(true), GameSessionDocument.class);

    }

/*
    public Mono<GameSessionDocument> findLastByToken(String token) {
        return getRepository().findFirstByTokenOrderByIdDesc(token);
    }*/

    /*public Mono<GameSessionDocument> findOneByExternalToken(String token) {
        return getRepository().findOneByExternalTokenAndStatus(token, Status.ACTIVE);
    }*/
}
