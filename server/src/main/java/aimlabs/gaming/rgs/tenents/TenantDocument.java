package aimlabs.gaming.rgs.tenents;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Document("Tenants")
public class TenantDocument extends EntityDocument {

    String uid;

    String name;

    List<String> domains = new ArrayList<>();
}
