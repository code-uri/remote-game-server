
import aimlabs.gaming.rgs.RemoteGameServer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;


@SpringBootTest
public class AppServerTest extends AbstractGamesTest {

    public static void main(String args[]) {
        String mongoUrl = mongoDBContainer.getReplicaSetUrl();
        Integer redisFirstMappedPort = redis.getFirstMappedPort();
        new SpringApplicationBuilder(RemoteGameServer.class)
                .properties("spring.data.mongodb.uri=" + mongoUrl,
                        "spring.data.redis.port=" + redisFirstMappedPort)
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
