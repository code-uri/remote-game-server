package aimlabs.gaming.rgs.permissions;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.core.dto.SearchRequest;
import aimlabs.gaming.rgs.core.dto.SearchResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
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
        SearchRequest request = new SearchRequest();
        request.getProperties().put("createdBy", "SYSTEM");
        List<PermissionDocument> permissionDocuments = store.search(request).getItems();
        if(permissionDocuments.isEmpty()){
            List<PermissionDocument> insertPermissions = securedResources.stream().flatMap(p -> {

                p = p.replace("/admin/", "");

                List<PermissionDocument> permissionDocumentList = new ArrayList<>();
                PermissionDocument read = new PermissionDocument();
                read.setPermission("read:" + p);
                read.setName("Read " + p);
                read.setCreatedBy("SYSTEM");
                read.setTenant("default");
                read.setEntity(p);

                PermissionDocument write = new PermissionDocument();
                write.setPermission("write:" + p);
                write.setName("Write " + p);
                write.setCreatedBy("SYSTEM");
                write.setTenant("default");
                write.setEntity(p);

                PermissionDocument delete = new PermissionDocument();
                delete.setPermission("delete:" + p);
                delete.setName("Delete " + p);
                delete.setCreatedBy("SYSTEM");
                delete.setTenant("default");
                delete.setEntity(p);

                PermissionDocument list = new PermissionDocument();
                list.setPermission("list:" + p);
                list.setName("List " + p);
                list.setCreatedBy("SYSTEM");
                list.setTenant("default");
                list.setEntity(p);


                permissionDocumentList.add(read);
                permissionDocumentList.add(write);
                permissionDocumentList.add(delete);
                permissionDocumentList.add(list);

                return permissionDocumentList.stream();
            }).toList();

             store.saveAll(insertPermissions);
        }

    }

    public List<Permission> findByUserId(String userId) {
        return store.findByUserId(userId);
    }
}
