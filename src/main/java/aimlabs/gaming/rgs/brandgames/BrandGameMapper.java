package aimlabs.gaming.rgs.brandgames;


import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface BrandGameMapper extends EntityMapper<BrandGame, BrandGameDocument> {

}
