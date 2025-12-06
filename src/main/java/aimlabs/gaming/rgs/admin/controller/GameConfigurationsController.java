package aimlabs.gaming.rgs.admin.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import aimlabs.gaming.rgs.security.SecuredEndpoint;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Data
@Slf4j
@RestController
@RequestMapping("/admin/game-configurations")
@SecuredEndpoint
public class GameConfigurationsController {

    // TODO: Re-implement with blocking RGSService
    /*
     * @Autowired
     * private RGSServiceFactory rgsServiceFactory;
     * 
     * @Autowired
     * List<RGSService> rgsServices;
     * 
     * @GetMapping("")
     * public List<String> getGameConfigurations() {
     * return rgsServices.stream()
     * .flatMap(rgsService -> rgsService.supportedGameConfigurations().stream())
     * .sorted()
     * .toList();
     * }
     * 
     * @GetMapping("/{uid}")
     * public ObjectNode getGameConfigurations(@PathVariable("uid") String uid) {
     * return rgsServiceFactory.getEngineAdaptor(uid)
     * .getGameClientConfig(uid);
     * }
     */
}
