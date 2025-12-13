package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.engine.discovery.GameEnginesLoadedEvent;
import aimlabs.gaming.rgs.engine.discovery.LoadGameEngineEvent;
import aimlabs.gaming.rgs.security.SecuredEndpoint;
import aimlabs.gaming.rgs.settings.GameSettingsService;
import aimlabs.gaming.rgs.settings.IGameSettingsService;
import aimlabs.gaming.rgs.gameskins.IGameSkinService;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@Data
@SecuredEndpoint
@Slf4j
public class GamesAdminController implements CommandLineRunner {
    public static final String REFRESH_CONNECTOR_SETTINGS = "refresh-connector-settings";

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    IGameSkinService gameSkinService;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    GameSettingsService gameSettingsService;

    @Autowired(required = false)
    RedisMessageListenerContainer redisMessageListenerContainer;

    @GetMapping("/cache/refresh/{channel}")
    public ResponseEntity<Void> refreshCache(@PathVariable String channel,
            @RequestParam String key) {
        stringRedisTemplate.convertAndSend(channel, key);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/games/generate-verification-report")
    public ResponseEntity<Void> generateVerificationReport(
            @RequestParam(defaultValue = "ON_DEMAND") String requested,
            @RequestParam(defaultValue = "false") String register) {
        applicationEventPublisher.publishEvent(
                new LoadGameEngineEvent(requested, Boolean.parseBoolean(register)));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stake-settings")
    public Map<String, Object> findGameStakeSettings(
            @RequestParam String tenant,
            @RequestParam String brand,
            @RequestParam(required = false) String game,
            @RequestParam(required = false) String currency) {

        if (game != null) {
            return gameSettingsService.findStakeSettings(tenant, brand, game, currency);
        } else {
            log.info("fetching stakes for all games");
            List<GameSkin> gameSkins = gameSkinService.findAll();
            Map<String, Object> result = new HashMap<>();
            for (GameSkin gameSkin : gameSkins) {
                Map<String, Object> stakeSettings = gameSettingsService
                        .findStakeSettings(tenant, brand, gameSkin.getUid(), currency);
                result.put(gameSkin.getUid(), stakeSettings);
            }
            return result;
        }
    }

    @Override
    public void run(String... args) throws Exception {
        if (redisMessageListenerContainer != null) {
            redisMessageListenerContainer.addMessageListener(
                    (MessageListener) (message, pattern) -> {
                        String refresh = new String(message.getBody());
                        log.info("Received message: {}", refresh);
                        String key = refresh.replace(REFRESH_CONNECTOR_SETTINGS + ":", "").trim();
                        // Parse the key and publish event
                        // Note: PAMConnectorModifiedEvent.unwrapKey needs to be adapted
                        // or the key parsing logic needs to be implemented here
                        log.info("Processing connector refresh for key: {}", key);
                    },
                    new ChannelTopic(REFRESH_CONNECTOR_SETTINGS));
        }
    }
}
