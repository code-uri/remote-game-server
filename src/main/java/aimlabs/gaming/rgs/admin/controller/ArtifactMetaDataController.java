package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.core.AbstractEntityCurdController;
import aimlabs.gaming.rgs.engine.artifact.ArtifactMetaData;
import aimlabs.gaming.rgs.engine.artifact.ArtifactMetaDataService;
import aimlabs.gaming.rgs.security.SecuredEndpoint;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@RestController
@RequestMapping("/admin/artifacts-metadata")
@SecuredEndpoint
public class ArtifactMetaDataController extends AbstractEntityCurdController<ArtifactMetaData> {

    @Autowired
    private ArtifactMetaDataService service;

}
