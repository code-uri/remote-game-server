package aimlabs.gaming.rgs;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;


@SpringBootTest(classes = RemoteGameServer.class)
public class AppServerTest extends AbstractGamesTest {

         @BeforeAll
        static void seedMongo() {
                importFiles.addAll(List.of(
                "/mongo/Networks.json",
                "/mongo/Brands.json",
                "/mongo/BrandGames.json",
                "/mongo/Games.json",
                "/mongo/GameSettings.json",
                "/mongo/Settings.json",
                "/mongo/Users.json",
                "/mongo/UserCredentials.json",
                "/mongo/Connectors.json"
                ));

                importFiles.forEach(AbstractGamesTest::importData);
        }

        @Test
        void contextLoads() {

        }

}
