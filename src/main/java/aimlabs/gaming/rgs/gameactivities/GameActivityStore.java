package aimlabs.gaming.rgs.gameactivities;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Data
@Slf4j
@Service
public class GameActivityStore  {

    public static final String DOCUMENT = "GameActivities";
    @Autowired
    private MongoTemplate template;

    public DBObject findLastActivityByGamePlay(String gamePlay) {
         return getTemplate().findOne(Query.query(Criteria.where("gamePlay")
                        .is(gamePlay).and("deleted").is(false))
                .with(Sort.by(Sort.Direction.DESC, "_id"))
                .limit(1), DBObject.class, DOCUMENT);

    }

    public DBObject insertDBObject(BasicDBObject gameActivityDBObject) {
        return getTemplate().insert(gameActivityDBObject, DOCUMENT);
    }

    public DBObject findOneDBObjectByUid(String gameActivity) {
        return getTemplate().findOne(Query.query(Criteria.where("uid")
                .is(gameActivity).and("deleted").is(false)), DBObject.class, DOCUMENT);
    }

    public DBObject updateAckResponse(String gameActivityUid, DBObject ackResponse) {
        return getTemplate().findAndModify(Query.query(Criteria.where("uid").is(gameActivityUid).and("deleted").is(false))
                , Update.update("ack", ackResponse)
                , DBObject.class, DOCUMENT);
    }

    public List<DBObject> findAllByGamePlay(String gamePlay) {
         return getTemplate().find(Query.query(Criteria.where("gamePlay")
                        .is(gamePlay).and("deleted").is(false))
                .with(Sort.by(Sort.Direction.ASC, "_id")), DBObject.class, DOCUMENT);
    }

   /* public Mono<Void> updateStreakCounter(String uid, StreakCounter streakCounter) {
        return getTemplate().updateFirst(Query.query(Criteria.where("uid")
                        .is(uid).and("deleted").is(false)) , new Update().set("streakCounter", streakCounter)  , DBObject.class,DOCUMENT).then();
    }*/
}