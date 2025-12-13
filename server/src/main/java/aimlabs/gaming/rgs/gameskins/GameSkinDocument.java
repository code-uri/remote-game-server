package aimlabs.gaming.rgs.gameskins;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "Games")
@CompoundIndexes({
        @CompoundIndex(name = "deleted_1_tenant_1_uid_1", def = "{deleted: 1,tenant: 1,  uid : 1}", unique = true)
})
public class GameSkinDocument extends EntityDocument {

    private String uid;

    private String name;

    private String description;

    private String gameType;

    private Integer payLines;

    private Double maxStakePerLine;

    private Double maxStake;

    private Integer []  stakeLadder;

    private String url;

    private String apiUrl;

    private String assetsBaseUrl;

    private String launchType = "REDIRECT";

    private String clientVersion;

    private String gameConfiguration;

    private String providerGame;

    private String connector = "local-connector";

    private String network = "default";

}
