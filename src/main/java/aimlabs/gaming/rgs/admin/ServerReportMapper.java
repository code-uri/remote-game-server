package aimlabs.gaming.rgs.admin;

import org.mapstruct.Mapper;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import aimlabs.gaming.rgs.engine.verification.ServerReport;

@Mapper(config = SpringEntityMapperConfig.class)
public interface ServerReportMapper extends EntityMapper<ServerReport, ServerReportDocument> {
}
