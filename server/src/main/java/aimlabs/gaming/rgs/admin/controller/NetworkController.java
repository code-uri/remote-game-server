package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.networks.Network;
import aimlabs.gaming.rgs.networks.NetworkService;
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
@RequestMapping("/admin/networks")
@SecuredEndpoint
public class NetworkController extends AbstractEntityCurdController<Network> {

    @Autowired
    NetworkService service;
}
