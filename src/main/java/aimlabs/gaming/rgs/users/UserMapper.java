package aimlabs.gaming.rgs.users;

import org.mapstruct.Mapper;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;

@Mapper(config = SpringEntityMapperConfig.class)
public interface UserMapper extends EntityMapper<User, UserDocument> {
}
