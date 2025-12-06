package aimlabs.gaming.rgs.tenents;

import lombok.Data;
import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.tenents.TenantDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Data
@Component
@Slf4j
public class TenantStore extends MongoEntityStore<TenantDocument> {

}
