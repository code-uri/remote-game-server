package aimlabs.gaming.rgs.networks;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "Networks")
public class NetworkDocument extends EntityDocument {

    String uid;
    String name;
    String clientId;
    String clientKey;
    List<String> connectors;
    List<String> settings;
}
