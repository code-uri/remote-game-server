package aimlabs.gaming.rgs.permissions;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = SpringEntityMapperConfig.class)
public interface PermissionMapper extends EntityMapper<Permission, PermissionDocument> {
}
