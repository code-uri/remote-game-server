package aimlabs.gaming.rgs.gameskins;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface GameSkinMapper extends EntityMapper<GameSkin, GameSkinDocument> {
}