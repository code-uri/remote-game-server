package aimlabs.gaming.rgs.connectors;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface ConnectorMapper extends EntityMapper<Connector, ConnectorDocument> {
}
