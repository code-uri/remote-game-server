package aimlabs.gaming.rgs.brands;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Document(collection = "Brands")
@CompoundIndexes({
        @CompoundIndex(name = "deleted_1_tenant_1_network_1_uid_1", def = "{deleted: 1,tenant: 1,network:1, uid : 1}", unique = true),
//        @CompoundIndex(name = "deleted_1_tenant_1_uid_1_network_1", def = "{deleted: 1,tenant: 1, uid : 1, network:1}", unique = true)
})
public class BrandDocument extends EntityDocument {

    private String uid;

    private String network;

    private String name;

    private String currency;

    private BigDecimal demoBalance;

    //private UserRealmType realmType;

    private String realm;

    private String parent;

    private boolean useOperatorToken;

    private String connectorUid;

    private Map<String,String> urls = new HashMap<>();

    private String jurisdiction;

    private Long realityCheckIntervalInMilliSeconds;

}
