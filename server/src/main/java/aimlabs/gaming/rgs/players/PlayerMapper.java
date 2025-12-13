package aimlabs.gaming.rgs.players;


import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface PlayerMapper extends EntityMapper<Player, PlayerDocument> {

}
