package aimlabs.gaming.rgs.freespins;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface FreeSpinsAllotmentMapper extends EntityMapper<FreeSpinsAllotment, FreeSpinsAllotmentDocument> {

}
