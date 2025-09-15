package aimlabs.gaming.rgs.engine.artifact;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.engine.discovery.RGSEngineProperties;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Data
@Service
public class ArtifactMetaDataService extends AbstractEntityService<ArtifactMetaData, ArtifactMetaDataDocument> {

    @Autowired
    ArtifactMetaDataStore store;

    @Autowired
    ArtifactMetadataMapper mapper;

    @Autowired
    RGSEngineProperties rgsEngineProperties;


    public List<ArtifactMetaData> getComponentsMetaData() {
        return store.getTemplate().find(Query.query(Criteria.where("deleted").is(false)
                        .and("tenant").in(rgsEngineProperties.getTenantsEnabled())),
                ArtifactMetaData.class, "ArtifactMetaData");
    }
}
