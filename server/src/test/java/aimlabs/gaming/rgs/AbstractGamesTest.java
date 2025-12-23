// java
package aimlabs.gaming.rgs;

import org.springframework.test.context.DynamicPropertyRegistry;
    import org.springframework.test.context.DynamicPropertySource;

    import org.testcontainers.containers.GenericContainer;
    import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.lifecycle.Startables;
    import org.testcontainers.utility.DockerImageName;
    import org.testcontainers.utility.MountableFile;

    import java.util.ArrayList;
    import java.util.List;
import java.util.stream.Stream;

    public abstract class AbstractGamesTest {

        protected static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:6-alpine"))
            .withExposedPorts(6379);

        public static List<String> importFiles = new ArrayList<>();

        protected static final MongoDBContainer mongoDBContainer =
                new MongoDBContainer(DockerImageName.parse("mongo:6.0.21-jammy"))
                        .withExposedPorts(27017);

        static {
            Startables.deepStart(Stream.of(mongoDBContainer, redis)).join();
        }

        protected static void importData(String importFile) {
            String destFile = importFile.replace("/mongo", "");
            mongoDBContainer.copyFileToContainer(MountableFile.forClasspathResource(importFile), destFile);
            String collection = destFile.replace("/", "").replace(".json", "");
            try {
                mongoDBContainer.execInContainer(
                        "mongoimport", "-d", "test", "-c", collection,
                        "--file", destFile, "--jsonArray"
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @DynamicPropertySource
        static void mongoDbProperties(DynamicPropertyRegistry registry) {
            registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
            registry.add("spring.data.mongodb.database", () -> "test");

            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        }
    }