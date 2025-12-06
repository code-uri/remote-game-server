package aimlabs.gaming.rgs.settings;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import aimlabs.gaming.rgs.core.AbstractEntityService;

@Slf4j
@Data
@Service
public class SettingsTemplateService extends AbstractEntityService<SettingsTemplate, SettingsTemplateDocument> {

    @Autowired
    SettingsTemplateStore store;

    @Autowired
    SettingsTemplateMapper mapper;

}
