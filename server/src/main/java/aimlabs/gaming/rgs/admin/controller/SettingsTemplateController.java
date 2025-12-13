package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.security.SecuredEndpoint;
import aimlabs.gaming.rgs.settings.SettingsTemplate;
import aimlabs.gaming.rgs.settings.SettingsTemplateService;
import aimlabs.gaming.rgs.core.AbstractEntityCurdController;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@Slf4j
@RestController
@RequestMapping("/admin/settings")
@SecuredEndpoint
public class SettingsTemplateController extends AbstractEntityCurdController<SettingsTemplate> {

    @Autowired
    private SettingsTemplateService service;

    public SettingsTemplateService getService() {
        return service;
    }
}
