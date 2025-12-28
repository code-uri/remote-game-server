package aimlabs.gaming.rgs.gameoperators.mockoperator;


import aimlabs.gaming.rgs.players.Player;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.convert.KeyspaceConfiguration;
import org.springframework.data.redis.core.convert.MappingConfiguration;
import org.springframework.data.redis.core.index.IndexConfiguration;
import org.springframework.data.redis.core.index.IndexDefinition;
import org.springframework.data.redis.core.index.SimpleIndexDefinition;
import org.springframework.data.redis.core.mapping.RedisMappingContext;

import java.util.Collections;
import java.util.List;

@Configuration
public class RedisMockPlayerConfig {


    @Bean
    public RedisMappingContext keyValueMappingContext() {
        return new RedisMappingContext(
                new MappingConfiguration(new MyIndexConfiguration(), new MyKeyspaceConfiguration()));
    }

    public static class MyKeyspaceConfiguration extends KeyspaceConfiguration {


        protected Iterable<KeyspaceSettings> initialConfiguration() {
            return Collections.singleton(new KeyspaceSettings(Player.class, "mock-player"));
        }
    }

    public static class MyIndexConfiguration extends IndexConfiguration {


        protected Iterable<IndexDefinition> initialConfiguration() {
            return List.of(new SimpleIndexDefinition("mock-player", "uid", "uid"),
                    new SimpleIndexDefinition("mock-player", "correlationId", "correlationId"));
        }
    }
}
