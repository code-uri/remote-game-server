package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.security.SecuredEndpoint;
import aimlabs.gaming.rgs.admin.ServerReportService;
import aimlabs.gaming.rgs.core.AbstractEntityCurdController;
import aimlabs.gaming.rgs.engine.verification.ServerReport;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@RestController
@RequestMapping("/admin/server-reports")
@SecuredEndpoint
public class ServerReportController extends AbstractEntityCurdController<ServerReport> {

    @Autowired
    private ServerReportService service;

}
