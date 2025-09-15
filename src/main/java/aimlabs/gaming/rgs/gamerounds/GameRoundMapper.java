package aimlabs.gaming.rgs.gamerounds;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface GameRoundMapper extends EntityMapper<GameRound, GameRoundDocument> {
}
