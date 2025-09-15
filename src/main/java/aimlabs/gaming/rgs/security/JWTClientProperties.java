package aimlabs.gaming.rgs.security;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(
        prefix = "jwt.client"
)
@Data
@Slf4j
public class JWTClientProperties {
    private String clientId = "85RfmGnP2P6FUYeC";
    private String clientSecret = "GzQHAmaF2yUr9N3eGzQHAmaF2yUr9N3eGzQHAmaF2yUr9N3eGzQHAmaF2yUr9N3e";
    public Duration expireRefreshTokenAfter = Duration.ofHours(24L);
    public Duration expireAccessTokenAfter = Duration.ofMinutes(15L);

}
