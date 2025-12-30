package aimlabs.gaming.rgs;

import java.util.List;

import org.springframework.boot.builder.SpringApplicationBuilder;


public class RemoteGameServerLocalServer extends AbstractGamesTest {

    public static void main(String args[]) {
        String mongoUrl = mongoDBContainer.getReplicaSetUrl();
        Integer redisFirstMappedPort = redis.getFirstMappedPort();
        new SpringApplicationBuilder(RemoteGameServer.class)
                .properties("debug=false",
                        "spring.data.mongodb.uri=" + mongoUrl,
                        "spring.data.redis.port=" + redisFirstMappedPort,
                        "server.servlet.context-path=/api/rgs",
                        "logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{traceId:-},%X{spanId:-}] %logger{36} - %replace(%msg){'[\\n\\r]', ''}%n")
                .run(args);
        /*Arrays.stream(Path.of("server/src/test/resources/mongo").toFile().listFiles())
                .sequential().forEachOrdered(file -> {

        });*/
        importFiles.addAll(List.of("/mongo/Networks.json",
                "/mongo/Brands.json",
                "/mongo/BrandGames.json",
                "/mongo/Games.json",
                "/mongo/GameSettings.json",
                "/mongo/Settings.json",
                "/mongo/Users.json",
                "/mongo/UserCredentials.json",
                "/mongo/Connectors.json"));
        importFiles.forEach(importFile -> {
            importData(importFile);
        });

    }
}
