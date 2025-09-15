package aimlabs.gaming.rgs.streaks;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface StreakCounterMapper extends EntityMapper<StreakCounter, StreakCounterDocument> {

}
