package aimlabs.gaming.rgs.token;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.entity.Status;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class TokenStore extends MongoEntityStore<TokenDocument> {

    public TokenDocument findOneByRefreshTokenAndStatus(String refreshToken, Status active) {
        return getTemplate().findOne(Query.query(Criteria.where("refreshToken")
                .is(refreshToken).and("status").is(active).and("deleted").is(false)), TokenDocument.class);
    }
}
