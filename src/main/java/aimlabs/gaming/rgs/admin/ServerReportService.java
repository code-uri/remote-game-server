package aimlabs.gaming.rgs.admin;   

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.engine.verification.ServerReport;

@Data
@Service
public class ServerReportService extends AbstractEntityService<ServerReport, ServerReportDocument> {

    @Autowired
    ServerReportStore store;

    @Autowired
    ServerReportMapper mapper;

}
