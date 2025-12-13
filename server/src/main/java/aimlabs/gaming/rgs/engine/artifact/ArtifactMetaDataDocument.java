package aimlabs.gaming.rgs.engine.artifact;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "ArtifactMetaData")
@CompoundIndexes({
        @CompoundIndex(name = "deleted_1_tenant_1_name_1", def = "{deleted: 1, tenant: 1, name : 1}", unique = true)
})
public class ArtifactMetaDataDocument extends EntityDocument {

    String name;
    String version;
    String digest;
    ArtifactMetaData.Type type;
    boolean critical;
}
