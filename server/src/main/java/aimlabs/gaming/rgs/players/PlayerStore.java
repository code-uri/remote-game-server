package aimlabs.gaming.rgs.players;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Data
@Slf4j
@Service
public class PlayerStore extends MongoEntityStore<PlayerDocument> {


    public Player findOneByNetworkAndBrandAndCorrelationId(String network, String brand,  String correlationId) {

        return getTemplate().findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                .and("network").is(network)
                .and("brand").is(brand)
                .and("correlationId").is(correlationId)
                .and("deleted").is(false)), Player.class, "Players");

    }

}
