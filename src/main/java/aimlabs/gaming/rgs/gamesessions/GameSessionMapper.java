package aimlabs.gaming.rgs.gamesessions;



import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface GameSessionMapper extends EntityMapper<GameSession, GameSessionDocument> {
}
