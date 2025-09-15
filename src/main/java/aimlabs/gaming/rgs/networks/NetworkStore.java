package aimlabs.gaming.rgs.networks;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Data
@Slf4j
@Service
public class NetworkStore extends MongoEntityStore<NetworkDocument> {



}