package aimlabs.gaming.rgs.permissions;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@Service
@Slf4j
public class PermissionService extends AbstractEntityService<Permission, PermissionDocument> {

    @Autowired
    private PermissionStore store;

    @Autowired
    private PermissionMapper mapper;

    @Autowired
    @Qualifier("securedResources")
    Set<String> securedResources;

    @Autowired
    @Qualifier("tenants")
    Set<String> tenants;



    @EventListener
    public void handleContextRefreshEvent(ContextRefreshedEvent contextStartedEvent) {
        if (store.countByCreatedBy("SYSTEM") == 0) {
            log.info("No SYSTEM permissions found. Seeding permissions for tenants: {}", tenants);
            List<PermissionDocument> permissionsToInsert = new ArrayList<>();
            for (String tenant : tenants) {
                for (String resource : securedResources) {
                    String cleanResource = resource.replace("/admin/", "");
                    permissionsToInsert.add(createPermission(tenant, cleanResource, "read"));
                    permissionsToInsert.add(createPermission(tenant, cleanResource, "write"));
                    permissionsToInsert.add(createPermission(tenant, cleanResource, "delete"));
                    permissionsToInsert.add(createPermission(tenant, cleanResource, "list"));
                }
            }
            store.saveAll(permissionsToInsert);
            log.info("Seeded {} SYSTEM permissions.", permissionsToInsert.size());
        }
    }

    private PermissionDocument createPermission(String tenant, String resource, String action) {
        PermissionDocument permission = new PermissionDocument();
        permission.setPermission(action + ":" + resource);
        permission.setName(action.substring(0, 1).toUpperCase() + action.substring(1) + " " + resource);
        permission.setCreatedBy("SYSTEM");
        permission.setTenant(tenant);
        permission.setEntity(resource);
        return permission;
    }

    public List<Permission> findByUserId(String userId) {
        return store.findByUserId(userId);
    }
}
