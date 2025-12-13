package aimlabs.gaming.rgs.connectors;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Data
@Slf4j
@Service
public class ConnectorStore extends MongoEntityStore<ConnectorDocument> {

    public ConnectorDocument findOneByTenantAndConnector(String tenant, String uid) {
        return getTemplate().findOne(query(where("deleted").is(false)
                .and("tenant").is(tenant)
                .and("uid").is(uid)), ConnectorDocument.class);
    }

    public ConnectorDocument findOneByTenant(String tenant) {
        return getTemplate().findOne(query(where("deleted").is(false)
                .and("tenant").is(tenant)), ConnectorDocument.class);
    }
}
