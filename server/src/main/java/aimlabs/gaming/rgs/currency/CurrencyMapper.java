package aimlabs.gaming.rgs.currency;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(
    config = SpringEntityMapperConfig.class
)
public interface CurrencyMapper extends EntityMapper<Currency, CurrencyDocument> {
}
