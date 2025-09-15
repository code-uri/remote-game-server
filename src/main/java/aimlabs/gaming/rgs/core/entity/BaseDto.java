package aimlabs.gaming.rgs.core.entity;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
public class BaseDto implements IEntity {

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
