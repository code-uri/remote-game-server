package aimlabs.gaming.rgs.users;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class UserCredentialStore extends MongoEntityStore<UserCredentialDocument> {

    public UserCredentialDocument findOneByUserName(String tenant, String username) {
        return getTemplate().findOne(Query.query(Criteria.where("deleted").is(false)
                .and("tenant").is(tenant)
                .and("username").is(username)),
                UserCredentialDocument.class);
    }
}
