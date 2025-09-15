package aimlabs.gaming.rgs.settings;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface GameSettingsMapper extends EntityMapper<GameSettings, GameSettingsDocument> {
}
