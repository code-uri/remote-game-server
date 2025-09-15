package aimlabs.gaming.rgs.engine.discovery;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "rgs.engines")
public class RGSEngineProperties {

    private boolean enableVerification;
    private boolean enableComponentsMetaData;
    private Map<String, String> hashes;
    private String dir = "/workspace/engines";
    private List<String> tenantsEnabled = List.of("default");
}