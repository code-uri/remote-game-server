package aimlabs.gaming.rgs.gameplay;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.games.TenantContextHolder;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.Objects;

@Data
@Slf4j
@Service
public class GamePlayStore extends MongoEntityStore<GamePlayDocument> {


    public DBObject findOneDBObjectByUidAndStatus(String uid, Status status) {
        return getTemplate().findOne(Query.query(Criteria.where("uid").is(uid).and("status").is(status).and("deleted").is(false)),
                DBObject.class, "GamePlays");
    }

    public DBObject insertDBObject(BasicDBObject gamePlay) {
        return getTemplate().insert(gamePlay, "GamePlays");
    }

    public DBObject updateOrInsert(String uid, Map<String, Object> gamePlayMap) {


        Update update = new Update();
        update.set("modifiedOn", new Date());
        Objects.requireNonNull(update);
        gamePlayMap.forEach(update::set);
        update.set("tenant", TenantContextHolder.getTenant());
        return this.getTemplate().findAndModify(Query.query(Criteria.where("uid").is(uid)
                        .and("tenant").is(TenantContextHolder.getTenant()).and("deleted").is(false)),
                update, FindAndModifyOptions.options().returnNew(true).upsert(true), DBObject.class, "GamePlays");
    }
}