package aimlabs.gaming.rgs.core.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface IEntity extends Serializable {

    boolean isDeleted();

    void setDeleted(boolean enabled);

    String getModifiedBy();

    void setModifiedBy(String id);

    Date getModifiedOn();

    void setModifiedOn(Date date);

    String getCreatedBy();

    void setCreatedBy(String id);

    Date getCreatedOn();

    void setCreatedOn(Date date);

    void setTenant(String tenant);

    String getTenant();

    void setAccount(String account);

    String getAccount();

    void setTags(List<String> tags);

    List<String> getTags();
    void setData(Map<String, Object> data);

    Map<String, Object> getData();

    void setStatus(Status status);

    Status getStatus();

    void setId(String id);

    String getId();

    String getCorrelationId();

    void setCorrelationId(String correlationId);
}
