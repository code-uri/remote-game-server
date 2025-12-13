package aimlabs.gaming.rgs.promotions;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface PromotionMapper extends EntityMapper<Promotion, PromotionDocument> {

}
