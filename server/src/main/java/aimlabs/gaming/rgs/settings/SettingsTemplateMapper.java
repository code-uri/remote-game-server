package aimlabs.gaming.rgs.settings;

import org.mapstruct.Mapper;

import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.mapper.SpringEntityMapperConfig;

@Mapper(config = SpringEntityMapperConfig.class)
public interface SettingsTemplateMapper extends EntityMapper<SettingsTemplate, SettingsTemplateDocument> {
}
