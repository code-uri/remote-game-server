package aimlabs.gaming.rgs.roles;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document("Roles")
@CompoundIndexes({
        @CompoundIndex(name = "deleted_1_tenant_1_uid_1", def = "{deleted: 1,tenant: 1, uid: 1}", unique = true)
})
public class RoleDocument extends EntityDocument {

    String uid;
    String name;
    List<String> permissions;

}