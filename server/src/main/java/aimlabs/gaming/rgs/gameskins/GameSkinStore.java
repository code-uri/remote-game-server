package aimlabs.gaming.rgs.gameskins;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Data
@Slf4j
@Service
public class GameSkinStore extends MongoEntityStore<GameSkinDocument> {



    public GameSkinDocument findOneByGame( String game) {

        return getTemplate().findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                .and("deleted").is(false)
                //.and("brand").regex(brand, "i")
                .and("uid").is(game)
                .and("status").is(Status.ACTIVE)), GameSkinDocument.class);
    }


    public GameSkinDocument findOneByProviderGame(String providerGame) {
        return getTemplate()
                .findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                                .and("deleted").is(false)
                                //.and("brand").regex(brand, "i")
                                .and("providerGame").is(providerGame)
                                .and("status").is(Status.ACTIVE)
                        ),
                        GameSkinDocument.class);
    }
}