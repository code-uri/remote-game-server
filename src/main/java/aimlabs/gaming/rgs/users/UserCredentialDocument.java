package aimlabs.gaming.rgs.users;

import aimlabs.gaming.rgs.core.documents.EntityDocument;

import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("UserCredentials")
@CompoundIndexes({
        @CompoundIndex(name = "deleted_1_tenant_1_username_1", def = "{deleted: 1,tenant: 1, username: 1}", unique = true)
})
public class UserCredentialDocument extends EntityDocument {

    String identity;

    String username;

    String password;
}
