package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.core.AbstractEntityCurdController;
import aimlabs.gaming.rgs.permissions.Permission;
import aimlabs.gaming.rgs.permissions.PermissionService;
import aimlabs.gaming.rgs.security.SecuredEndpoint;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@Slf4j
@RestController
@RequestMapping("/admin/permissions")
@SecuredEndpoint
public class PermissionController extends AbstractEntityCurdController<Permission> {

    @Autowired
    PermissionService service;

    /*
     * @GetMapping("/")
     * public Mono<List<String>> findAll() {
     * return Flux.fromIterable(securedResources)
     * //.doOnNext(s -> log.info("resource {}", s))
     * .map(s -> s.replace("/admin/",""))
     * .collectSortedList();
     * }
     */

}