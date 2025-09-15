package aimlabs.gaming.rgs.engine.artifact;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface ArtifactMetadataMapper extends EntityMapper<ArtifactMetaData, ArtifactMetaDataDocument> {
}
