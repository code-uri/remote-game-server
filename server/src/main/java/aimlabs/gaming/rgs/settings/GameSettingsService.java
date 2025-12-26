package aimlabs.gaming.rgs.settings;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Service
public class GameSettingsService
        implements IGameSettingsService, CommandLineRunner {

    public static final String GAME_SETTINGS = "game-settings";
    // public static final String STAKE_SETTINGS = "stake-settings";
    // public static final String AUTO_PLAY_SETTINGS = "auto-play-settings";

    @Autowired
    GameSettingsStore store;

    @Autowired
    GameSettingsMapper mapper;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    @Autowired
    SettingsTemplateStore settingsTemplateStore;

    @Autowired
    RedisMessageListenerContainer redisMessageListenerContainer;

    /*
     * public Mono<GameSettings> findOne(String uid) {
     * return this.getStore().findOneByUid(uid)
     * .map(getMapper()::asDto);
     * }
     */

    private ConcurrentHashMap<String, ConcurrentHashMap<String, JsonNode>> cacheMap = new ConcurrentHashMap<>();

    /*
     * public GameSettingsService(ReactiveRedisConnectionFactory factory){
     * JdkSerializationRedisSerializer jdkSerializer = new
     * JdkSerializationRedisSerializer(this.getClass().getClassLoader());
     * RedisSerializationContext<Object, Object> serializationContext =
     * RedisSerializationContext.newSerializationContext()
     * .key(jdkSerializer)
     * .value(jdkSerializer)
     * .hashKey(jdkSerializer).hashValue(jdkSerializer).build();
     * reactiveRedisTemplate = new ReactiveRedisTemplate<>(factory,
     * serializationContext);
     * }
     */

    @Autowired
    private ObjectMapper objectMapper;

    public GameSettingsService() {
        cacheMap.put(GAME_SETTINGS, new ConcurrentHashMap<>());
        // cacheMap.put(STAKE_SETTINGS, new ConcurrentHashMap<>());
        // cacheMap.put(AUTO_PLAY_SETTINGS, new ConcurrentHashMap<>());
    }

    public static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {

        Iterator<String> fieldNames = updateNode.fieldNames();

        while (fieldNames.hasNext()) {
            String updatedFieldName = fieldNames.next();
            JsonNode valueToBeUpdated = mainNode.get(updatedFieldName);
            JsonNode updatedValue = updateNode.get(updatedFieldName);

            // If the node is an @ArrayNode
            if (valueToBeUpdated != null && valueToBeUpdated.isArray() &&
                    updatedValue.isArray()) {

                // log.info("fieldName {} valueToBeUpdated {} updatedValue {} {}
                // ",updatedFieldName,valueToBeUpdated, updatedValue,
                // !updatedValue.get(0).isObject());
                if (!updatedValue.isEmpty() && !updatedValue.get(0).isObject()) {
                    ((ObjectNode) mainNode).set(updatedFieldName, updatedValue);
                    continue;
                }

                // running a loop for all elements of the updated ArrayNode
                for (int i = 0; i < updatedValue.size(); i++) {
                    JsonNode updatedChildNode = updatedValue.get(i);
                    if (updatedChildNode == null)
                        continue;
                    // Create a new Node in the node that should be updated, if there was no
                    // corresponding node in it
                    // Use-case - where the updateNode will have a new element in its Array
                    if (valueToBeUpdated.size() <= i) {
                        ((ArrayNode) valueToBeUpdated).add(updatedChildNode);
                        break;
                    }
                    // getting reference for the node to be updated
                    JsonNode childNodeToBeUpdated = valueToBeUpdated.get(i);
                    if (childNodeToBeUpdated.isObject() && updatedChildNode.isObject())
                        merge(childNodeToBeUpdated, updatedChildNode);
                    else
                        ((ArrayNode) valueToBeUpdated).set(i, updatedChildNode);
                }
                // if the Node is an @ObjectNode
            } else if (valueToBeUpdated != null && valueToBeUpdated.isObject()) {
                merge(valueToBeUpdated, updatedValue);
            } else {

                if (updatedValue != null && updatedValue.getNodeType() != JsonNodeType.POJO
                        && updatedValue.getNodeType() != JsonNodeType.OBJECT) {
                    // log.info("merge replace {} dataType {} with {}",updatedFieldName,
                    // updatedValue.getNodeType(), updatedValue);
                    ((ObjectNode) mainNode).set(updatedFieldName, updatedValue);
                    // log.info("after merge {}",mainNode);
                } else if (updatedValue != null) {

                    if (valueToBeUpdated == null) {
                        valueToBeUpdated = JsonNodeFactory.instance.objectNode();
                        ((ObjectNode) mainNode).set(updatedFieldName, valueToBeUpdated);
                    }
                    // log.info("merge updatedFieldName {} dataType {} with {}",valueToBeUpdated,
                    // updatedValue.getNodeType(), updatedValue);
                    merge(valueToBeUpdated, updatedValue);
                }

            }
        }
        return mainNode;
    }

    public static boolean isJackpotEnabled(Map<String, Object> settings) {
        if (settings.get("jackpotEnabled") != null) {
            if (settings.get("jackpotEnabled") instanceof String)
                return Boolean.parseBoolean((String) settings.get("jackpotEnabled"));
            else
                return (Boolean) settings.get("jackpotEnabled");
        }
        return false;
    }

    public static boolean isLockingPlayerRequired(Map<String, Object> settings) {

        if (settings.get("lockPlayer") != null) {
            if (settings.get("lockPlayer") instanceof String)
                return Boolean.parseBoolean((String) settings.get("lockPlayer"));
            else
                return (Boolean) settings.get("lockPlayer");
        }

        return false;
    }

    public static boolean isConfirmHandSupported(Map<String, Object> settings) {
        if (settings.get("confirmHandSupported") != null) {
            if (settings.get("confirmHandSupported") instanceof String)
                return Boolean.parseBoolean((String) settings.get("confirmHandSupported"));
            else
                return (Boolean) settings.get("confirmHandSupported");
        }
        return false;
    }

    public static boolean isUnfinishedGamesSupported(Map<String, Object> settings) {
        if (settings.get("unfinishedGamesSupported") != null) {
            if (settings.get("unfinishedGamesSupported") instanceof String)
                return Boolean.parseBoolean((String) settings.get("unfinishedGamesSupported"));
            else
                return (Boolean) settings.get("unfinishedGamesSupported");
        }
        return false;
    }

    public static boolean isConfirmHandSupportedFromMap(Map<String, Object> settings) {
        if (settings.get("confirmHandSupported") != null) {
            if (settings.get("confirmHandSupported") instanceof String)
                return Boolean.parseBoolean((String) settings.get("confirmHandSupported"));
            else
                return (Boolean) settings.get("confirmHandSupported");
        }
        return false;
    }

    public static boolean isPlayerMapSupported(Map<String, Object> settings) {
        if (settings.get("playerMapSupported") != null) {
            if (settings.get("playerMapSupported") instanceof String)
                return Boolean.parseBoolean((String) settings.get("playerMapSupported"));
            else
                return (Boolean) settings.get("playerMapSupported");
        }
        return false;
    }

    public static boolean isForceUnfinished(Map<String, Object> settings) {
        return settings.containsKey("forceUnfinishedGames")
                && (boolean) settings.getOrDefault("forceUnfinishedGames", false);
    }

    public static boolean isReadPlayerBag(Map<String, Object> settings) {
        if (settings.get("readPlayerBag") != null) {
            if (settings.get("readPlayerBag") instanceof String)
                return Boolean.parseBoolean((String) settings.get("readPlayerBag"));
            else
                return (Boolean) settings.get("readPlayerBag");
        }
        return false;
    }

    public Map<String, Object> getBrandGameSettings(String tenant, String brand, String gameId) {

        JsonNode jsonNode = findGameSettings(tenant, brand, gameId);

        // log.info("brand: {} game: {} currency: {}, Game Settings : {} ",brand,
        // gameId, currency, jsonNode.toPrettyString());
        Map<String, Object> settingsMap = objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {
        });

        settingsMap.remove("stakes");
        return settingsMap;
    }

    public JsonNode getGameSettings(String tenant, String brand, String gameId) {
        String key = tenant + "::" + brand + "::" + gameId;

        Function<String, JsonNode> readFromDB = o -> (JsonNode) store.getSettings(tenant, brand, gameId)
                .stream()
                .map(template -> {
                    SettingsTemplateDocument settingsTemplateDocument = settingsTemplateStore.findOneByUid(tenant,
                            template);
                    return getObjectMapper().valueToTree(settingsTemplateDocument);
                })
                .reduce((jsonNode, jsonNode2) -> GameSettingsService.merge((JsonNode) jsonNode, (JsonNode) jsonNode2))
                .get();

        return monoCache(GAME_SETTINGS, key.toLowerCase(), readFromDB);
    }

    public JsonNode findGameSettings(String tenant, String brand, String gameId) {

        JsonNode settings = getGameSettings(tenant, brand.toLowerCase(), gameId.toLowerCase());
        if (settings != null)
            new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "No stake settings!");

        return settings;
    }

    @Override
    public Map<String, Object> findGameSettingsForCurrency(String tenant, String brand, String gameId,
            String currency) {
        JsonNode jsonNode = findGameSettings(tenant, brand.toLowerCase(), gameId.toLowerCase());

        // log.info("brand: {} game: {} currency: {}, Game Settings : {} ",brand,
        // gameId, currency, jsonNode.toPrettyString());
        Map<String, Object> settingsMap = objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {
        });

        settingsMap.remove("_id");
        settingsMap.remove("uid");
        settingsMap.remove("id");
        settingsMap.remove("deleted");
        settingsMap.remove("tenant");
        settingsMap.remove("createdOn");
        settingsMap.remove("modifiedOn");

        if (settingsMap.containsKey("defaultStakeSettings")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> defaultStakeSettings = (Map<String, Object>) settingsMap.remove("defaultStakeSettings");
            settingsMap.putAll(defaultStakeSettings);
        }
        if (settingsMap.containsKey("stakes")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stakes = (Map<String, Object>) settingsMap.remove("stakes");

            if (currency == null) {
                settingsMap.putAll(stakes);
            } else if (stakes.containsKey(currency)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> currencyStakes = (Map<String, Object>) stakes.get(currency);
                settingsMap.putAll(currencyStakes);
            }
        }

        if (settingsMap.containsKey("streakWagers")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stakes = (Map<String, Object>) settingsMap.remove("streakWagers");
            if (stakes.containsKey(currency)) {
                Double streakWager = ((Number) stakes.get(currency)).doubleValue();
                settingsMap.put("streakWager", streakWager);
            }
        }
        if (settingsMap.containsKey("data")) {

            @SuppressWarnings("unchecked")
            Map<String, Object> extraConfig = (Map<String, Object>) settingsMap.remove("data");

            // log.info("extra config {}", extraConfig);
            settingsMap.putAll(extraConfig);
            settingsMap.remove("reconcile");
            // ((ObjectNode)jsonNode).remove("autoPlayEnabled");
        }
        return settingsMap;

    }

    public Map<String, Object> findStakeSettings(String tenant, String brand, String gameId, String currency) {

        JsonNode jsonNode = findGameSettings(tenant, brand, gameId);
        // log.info("brand: {} game: {} currency: {}, Game Settings : {} ",brand,
        // gameId, currency, jsonNode.toPrettyString());
        Map<String, Object> settingsMap = objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> stakes = (Map<String, Object>) settingsMap.get("stakes");
        if (currency == null) {
            return stakes;
        } else if (stakes.containsKey(currency)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> currencyStakes = (Map<String, Object>) stakes.get(currency);
            return currencyStakes;
        }
        return stakes;
    }

    private <T> JsonNode monoCache(String keySpace, String key, Function<String, JsonNode> readFromDB) {

        if (!cacheMap.containsKey(keySpace)) {
            cacheMap.put(keySpace, new ConcurrentHashMap<>());
            JsonNode value = readFromDB.apply("");
            cacheMap.get(keySpace).put(key, value);
            return value;
        }

        JsonNode value = cacheMap.get(keySpace).get(key);
        if (value == null) {
            value = readFromDB.apply("");
            cacheMap.get(keySpace).put(key, value);
            return value;
        } else {
            return value;
        }
    }

    public Boolean findGameSettingsForAutoPlay(String tenant, String gameId, String brand) {

        JsonNode settings = getGameSettings(tenant, brand.toLowerCase(), gameId.toLowerCase());
        if (settings.has("autoPlayEnabled"))
            return settings.get("autoPlayEnabled").asBoolean();
        else
            return false;
    }

    public void handleEvent(String refresh) {
        log.info("Refresh {}.", refresh);
        // cacheMap.get(GAME_SETTINGS).clear();
        if (GAME_SETTINGS.concat(":*").equals(refresh) || "*".equals(refresh)) {
            log.info("Cleared game-settings cache.");
            cacheMap.get(GAME_SETTINGS).clear();
        } else if (refresh != null && refresh.contains("*")) {
            String game = refresh.substring(refresh.lastIndexOf("::") + 2);
            cacheMap.get(GAME_SETTINGS).keySet().stream().filter(s -> s.contains(game)).forEach(key -> {
                log.info("Clear cache for key {}", key);
                cacheMap.get(GAME_SETTINGS).remove(key);
            });
        } else if (StringUtils.hasText(refresh)) {
            // String key = refresh.split(":")[1];
            log.info("Clear cache for key {}", refresh);
            cacheMap.get(GAME_SETTINGS).remove(refresh);
        }
    }

    @EventListener
    public void handleContextRefreshEvent(SettingsChangeEvent settingsChangeEvent) {
        log.info("{}", settingsChangeEvent);
        handleEvent(settingsChangeEvent.getKey());
    }

    @Override
    public void run(String... args) throws Exception {
                          // Listen for cache refresh messages
        MessageListener refreshListener = (Message message, byte[] pattern) -> {
            try {
                   String refresh = new String(message.getBody());
                        handleEvent(refresh);
            } catch (Exception e) {
                log.error("Error processing refresh message", e);
            }
        };
    
        redisMessageListenerContainer.addMessageListener(refreshListener, new PatternTopic("refresh-game-settings"));

        // if (disposable == null) {
        // disposable = redisTemplate.watch(); listenToChannel("refresh-game-settings")
        // //.sample(Duration.ofMinutes(1))
        // .doOnNext(stringObjectMessage -> {
        // String refresh = stringObjectMessage.getMessage();
        // handleEvent(refresh);
        // }).subscribeOn(Schedulers.boundedElastic()).subscribe();
        // }
    }

    // public static ObjectNode copyStakes(String currency, JsonNode from,
    // ObjectNode to) {
    //
    // if (from.has("defaultStakeSettings")) {
    // to.setAll((ObjectNode) from.get("defaultStakeSettings"));
    // }
    // if (from.has("stakes")
    // && (from.get("stakes")).get(currency) != null) {
    // JsonNode currencyStakes = from.get("stakes").get(currency);
    // to.setAll((ObjectNode) currencyStakes);
    // }
    // return to;
    // }
}
