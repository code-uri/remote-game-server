package aimlabs.gaming.rgs.config;

import aimlabs.gaming.rgs.engine.discovery.RGSEngineProperties;
import aimlabs.gaming.rgs.gamesessions.GameSessionArgumentResolver;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import in.aimlabs.rng.RNG;
import in.aimlabs.rng.fortuna.Fortuna;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.convert.MappingRedisConverter;
import org.springframework.data.redis.core.convert.RedisCustomConversions;
import org.springframework.data.redis.core.convert.ReferenceResolver;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.WebExceptionHandler;
import org.zalando.jackson.datatype.money.MoneyModule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;


@Configuration
@ComponentScan(basePackages = "aimlabs")
//@EnableConfigurationProperties(JWTClientProperties.class)
@AutoConfigureBefore(JacksonAutoConfiguration.class)
@Slf4j
public class AppServerConfig {


    AppServerConfig(ObjectMapper objectMapper){
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        objectMapper.enable(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.disable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);



        objectMapper.registerModule(new MoneyModule());
    }


    @Bean
    GameSessionArgumentResolver gameSessionArgumentResolver() {
        return new GameSessionArgumentResolver();
    }

//    @Bean
//    public WebExceptionHandler webExceptionHandler() {
//        return new WebFluxResponseStatusExceptionHandler();
//    }

    @Bean
    RNG rng() {
        return new Fortuna();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("noop", NoOpPasswordEncoder.getInstance());
        encoders.put("bcrypt", new BCryptPasswordEncoder(4));
        //encoders.put("scrypt", new SCryptPasswordEncoder(1, 2, 1, 1, 10));

        var passwordEncoder = new DelegatingPasswordEncoder("bcrypt", encoders);
        passwordEncoder.setDefaultPasswordEncoderForMatches(encoders.get("noop"));

        return passwordEncoder;
    }

   /* @Bean
    @Primary
    PlayerServiceManager playerServiceManager(List<PlayerServiceProvider> providers) {
        return new PlayerAccountManagerDelegate(providers);
    }*/

    @Bean
    public MappingRedisConverter redisConverter(RedisMappingContext mappingContext,
                                                RedisCustomConversions redisCustomConversions, ReferenceResolver referenceResolver) {

        MappingRedisConverter mappingRedisConverter = new MappingRedisConverter(mappingContext, null, referenceResolver);
        mappingRedisConverter.setCustomConversions(redisCustomConversions);

        return mappingRedisConverter;
    }

    @Bean
    public RedisCustomConversions redisCustomConversions(ObjectMapper objectMapper) {
        return new RedisCustomConversions((asList(
                // writing converter, reader converter
               new ObjectNodeToBytesConverter(objectMapper),new BytesToObjectNodeConverter(objectMapper)
        )));
    }
    
}

