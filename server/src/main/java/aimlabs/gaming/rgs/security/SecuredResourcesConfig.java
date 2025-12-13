package aimlabs.gaming.rgs.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.TreeSet;

@Configuration
public class SecuredResourcesConfig {
    @Bean
    Set<String> securedResources() {
        return new TreeSet<>();
    }
}
