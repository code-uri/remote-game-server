package aimlabs.gaming.rgs.permissions;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("Permissions")
public class PermissionDocument extends EntityDocument {

    protected String userId;
    protected String permission;

    protected String actions;
    protected String name;
    protected String entity;

    // does this permission have a wildcard at the end?
    protected transient boolean wildcard;

    // the name without the wildcard on the end
    protected transient String path;
}
