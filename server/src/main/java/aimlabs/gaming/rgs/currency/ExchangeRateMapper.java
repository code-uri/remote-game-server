package aimlabs.gaming.rgs.currency;

import org.mapstruct.Mapper;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;

@Mapper(config = SpringEntityMapperConfig.class)
public interface ExchangeRateMapper extends EntityMapper<ExchangeRate, ExchangeRateDocument> {
}