package aimlabs.gaming.rgs.connectors;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@Data
@Document(collection = "Connectors")
@CompoundIndexes({
        @CompoundIndex(name = "deleted_1_tenant_1_uid_1", def = "{deleted: 1, tenant: 1, uid : 1}", unique = true)
})
public class ConnectorDocument extends EntityDocument {

    private String uid;
    private String baseUrl;
    private Map<String, Object> settings = new HashMap<>();
    private String parentConnector;
    private String network;
}