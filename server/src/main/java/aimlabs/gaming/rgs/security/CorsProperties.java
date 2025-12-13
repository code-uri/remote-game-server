package aimlabs.gaming.rgs.security;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(
        prefix = "rgs.cors"
)
@Data
@Slf4j
public class CorsProperties {

    List<CorsCfgEntry> endpoints = new ArrayList<>();
}
