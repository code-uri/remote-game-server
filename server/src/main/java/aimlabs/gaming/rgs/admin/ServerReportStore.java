package aimlabs.gaming.rgs.admin;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import aimlabs.gaming.rgs.core.MongoEntityStore;

@Data
@Slf4j
@Component
public class ServerReportStore extends MongoEntityStore<ServerReportDocument> {

}