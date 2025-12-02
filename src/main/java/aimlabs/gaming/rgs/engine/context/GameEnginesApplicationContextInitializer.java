package aimlabs.gaming.rgs.engine.context;

import aimlabs.gaming.rgs.engine.artifact.ArtifactMetaData;
import aimlabs.gaming.rgs.engine.artifact.ArtifactMetaDataService;
import aimlabs.gaming.rgs.engine.discovery.GameEnginesLoadedEvent;
import aimlabs.gaming.rgs.engine.discovery.LoadGameEngineEvent;
import aimlabs.gaming.rgs.engine.discovery.RGSEngineProperties;
import aimlabs.gaming.rgs.engine.discovery.RGSServiceDiscovery;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@Component
@Slf4j
public class GameEnginesApplicationContextInitializer implements ApplicationListener<LoadGameEngineEvent> {

    @Autowired
    RGSEngineProperties rgsEngineProperties;
    @Autowired
    ArtifactMetaDataService artifactMetaDataService;
    @Value("${rgs.engines.dir:/workspace/engines}")
    private String enginesDir;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private RGSServiceDiscovery rgsServiceDiscovery;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public void load(boolean register) throws IOException {
        rgsServiceDiscovery.gameEngineVerificationReports.clear();
        Path dir = Path.of(rgsEngineProperties.getDir());
        log.info("Started engines discovery at {} ", dir.toAbsolutePath());

        log.info("engines dir files {}", Files.list(dir).collect(Collectors.toList()));

        Stream<Path> directoryStream = Files.list(dir);


        Stream<ArtifactMetaData> artifactMetaDataStream = Stream.empty();


        if (rgsEngineProperties.isEnableComponentsMetaData()) {
            artifactMetaDataService.getComponentsMetaData().stream()
                    .filter(artifactMetaData -> artifactMetaData.getType() == ArtifactMetaData.Type.ENGINE)
                    .forEach(artifactMetaData -> {

//                        if (artifactMetaData.getType() != ArtifactMetaData.Type.ENGINE) {
//                            if (!rgsServiceDiscovery.test(Path.of(dir.toAbsolutePath() + "/" + artifactMetaData.getName()).toFile(), artifactMetaData.getDigest(), null)) {
//                                if (artifactMetaData.isCritical())
//                                    throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "Critical component " + artifactMetaData.getName() + " failed!");
//                            }
//                        }
                    });
        } else {
            directoryStream.map(path -> new ArtifactMetaData(path.toFile().getName(), ArtifactMetaData.Type.ENGINE))
                    .peek(artifactMetaData -> {
                        GameEngineApplicationContext engineApplicationContext =
                                new GameEngineApplicationContext(artifactMetaData, applicationContext, rgsServiceDiscovery);
                        if (register)
                            engineApplicationContext.start();

                        log.info("Engine {} initialisation completed", artifactMetaData.getName());
                    }).forEach(artifactMetaData -> {
                        log.debug("Loaded engine: {}", artifactMetaData.getName());
                    });
            
            if (rgsServiceDiscovery.getGameEngineServices().isEmpty()) {
                log.warn("Found no engines to load! {}", rgsEngineProperties);
            }
        }
        
        directoryStream.close();
    }


    public void onApplicationEvent(LoadGameEngineEvent event) {
        try {
            load(event.isRegister());
            applicationEventPublisher.publishEvent(new GameEnginesLoadedEvent(event.getSource(), event.getRefId(), "SUCCESS"));
            log.info("Game engines registered. reference id  {}", event.getRefId());
        } catch (Exception e) {
            log.error("", e);
            rgsServiceDiscovery.getGameEngineServices().clear();
            applicationEventPublisher.publishEvent(new GameEnginesLoadedEvent(event.getSource(), event.getRefId(), "FAILED"));
        }
    }

    @PostConstruct
    public void run() throws Exception {
        String refId = UUID.randomUUID().toString();
        try {
            load(true);
            GameEnginesLoadedEvent event = new GameEnginesLoadedEvent("ON_STARTUP", refId, "SUCCESS");
            applicationEventPublisher.publishEvent(event);
            log.info("Game engines registered. reference id  {}", refId);
        } catch (Exception e) {
            log.error("Engine discovery failed.", e);
            GameEnginesLoadedEvent event = new GameEnginesLoadedEvent("ON_STARTUP", refId, "FAILED");
            rgsServiceDiscovery.getGameEngineServices().clear();
            applicationEventPublisher.publishEvent(event);
            //int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            //System.exit(exitCode);
        }
    }
}
