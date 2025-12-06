package aimlabs.gaming.rgs.users;

import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import aimlabs.gaming.rgs.core.documents.EntityDocument;

import java.util.List;

@Data
@Document("Users")
@CompoundIndexes(value = {
        @CompoundIndex(def = "{'tenant': 1, 'username': 1}")
})
public class UserDocument extends EntityDocument {

    String username;

    String firstName;

    String lastName;

    String email;

    List<String> roles;
}
