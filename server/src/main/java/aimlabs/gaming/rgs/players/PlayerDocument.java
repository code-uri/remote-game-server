package aimlabs.gaming.rgs.players;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "Players")
@CompoundIndex(def = "{'deleted': 1, 'tenant': 1, 'network': 1, 'brand': 1,  'correlationId': 1}", unique = true)
public class PlayerDocument extends EntityDocument {
    @Indexed(name = "uid_1", unique = true)
    private String uid;
    private String firstName;
    private String lastName;
    private String network;
    private PlayerWallet wallet;
    private String brand;
}
