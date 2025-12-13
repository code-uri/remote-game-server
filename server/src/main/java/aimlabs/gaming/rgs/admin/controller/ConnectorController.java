package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.connectors.IConnectorService;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.security.SecuredEndpoint;
import aimlabs.gaming.rgs.core.AbstractEntityCurdController;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@Slf4j
@RestController
@RequestMapping("/admin/connectors")
@SecuredEndpoint
public class ConnectorController extends AbstractEntityCurdController<Connector> {

    @Autowired
    private IConnectorService service;

}
