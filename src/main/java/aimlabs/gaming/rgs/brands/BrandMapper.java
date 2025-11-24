package aimlabs.gaming.rgs.brands;



import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface BrandMapper extends EntityMapper<Brand, BrandDocument> {
}
