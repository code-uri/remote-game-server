package aimlabs.gaming.rgs.networks;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Data
@Service
public class NetworkService extends AbstractEntityService<Network, NetworkDocument> implements INetworkService {

    @Autowired
    NetworkStore store;

    @Autowired
    NetworkMapper mapper;

    public Network findOneByClientId(String clientId) {
        return store.getTemplate().findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                        .and("clientId").is(clientId).and("deleted").is(false)),

                Network.class, "Networks");
    }

    public Network findOneByConnector(String connectorUid) {
        return store.getTemplate().findOne(Query.query(Criteria.where("tenant")
                            .is(TenantContextHolder.getTenant())
                        .and("deleted").is(false)
                        .and("connectors").is(connectorUid)),
                Network.class, "Networks");
    }
}
