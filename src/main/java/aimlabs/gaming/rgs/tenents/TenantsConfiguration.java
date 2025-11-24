package aimlabs.gaming.rgs.tenents;

import aimlabs.gaming.rgs.engine.discovery.RGSEngineProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class TenantsConfiguration {

    @Bean
    Set<String> tenants(RGSEngineProperties rgsEngineProperties){
        return new HashSet<>(rgsEngineProperties.getTenantsEnabled());
    }
}
