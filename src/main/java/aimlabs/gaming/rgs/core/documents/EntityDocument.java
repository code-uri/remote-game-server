package aimlabs.gaming.rgs.core.documents;


import aimlabs.gaming.rgs.core.entity.BaseEntity;
import aimlabs.gaming.rgs.core.entity.IEntity;
import aimlabs.gaming.rgs.core.entity.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.*;

@Data
@NoArgsConstructor
public class EntityDocument extends BaseEntity implements IEntity {
    @Id
    protected String id;
    protected String correlationId;
    protected List<String> tags = new ArrayList<>();
    protected Map<String, Object> data = new HashMap<>();
    protected Status status;

    protected boolean deleted = false;

    protected String tenant;
    protected String account;

    protected String createdBy;
    protected Date createdOn = new Date();
    protected String modifiedBy;
    protected Date modifiedOn = new Date();
}
