package aimlabs.gaming.rgs.permissions;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PermissionStore extends MongoEntityStore<PermissionDocument> {

    public List<Permission> findByUserId(String userId) {
        return getTemplate().find(Query.query(Criteria.where("deleted").is(false)
                .and("tenant").is(TenantContextHolder.getTenant()).and("userId").is(userId)), Permission.class, "Permissions");
    }

    public long countByCreatedBy(String createdBy){
        return getTemplate().count(Query.query(Criteria.where("deleted").is(false)
                .and("createdBy").is(createdBy)), Permission.class, "Permissions");
    }

}
