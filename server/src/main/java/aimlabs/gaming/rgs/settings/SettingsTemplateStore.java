package aimlabs.gaming.rgs.settings;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Data
@Slf4j
@Service
public class SettingsTemplateStore extends MongoEntityStore<SettingsTemplateDocument> {


    public SettingsTemplateDocument findOneByUid(String tenant, String uid) {
        return getTemplate()
                .findOne(Query.query(Criteria.where("deleted").is(false)
                                        .and("tenant").is(tenant)
                                        .and("uid").is(uid))
                                .collation(Collation.of(Locale.ENGLISH)),
                        SettingsTemplateDocument.class);
    }
}