package aimlabs.gaming.rgs.roles;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface RoleMapper extends EntityMapper<Role, RoleDocument> {
}
