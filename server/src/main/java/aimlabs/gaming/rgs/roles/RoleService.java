package aimlabs.gaming.rgs.roles;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Data
@Service
public class RoleService extends AbstractEntityService<Role, RoleDocument> {

    @Autowired
    private RoleStore store;

    @Autowired
    private RoleMapper mapper;

}
