package aimlabs.gaming.rgs.cache.aspect;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import aimlabs.gaming.rgs.core.entity.BaseDto;
import aimlabs.gaming.rgs.core.event.EntityUpdateEvent;
import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.AbstractEntityService;
import lombok.extern.slf4j.Slf4j;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Aspect
@Slf4j
public class CacheEntitiesImplAspect implements ApplicationContextAware, CommandLineRunner {

    @Autowired(required = false)
    RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    RedisMessageListenerContainer redisMessageListenerContainer;

    public static final String INVALIDATE_CACHED_ENTITY = "invalidate-cached-entity";

    Set<String> watchedNameSpaces = new HashSet<>();
    Cache<String, BaseDto> cachedEntityByIdMap = Caffeine.newBuilder()
            .initialCapacity(100)
            // .maximumSize(500)
            .expireAfterAccess(Duration.ofMinutes(10)).build();

    Cache<String, CacheKey> cacheKeyMap = Caffeine.newBuilder()
            .initialCapacity(100)
            // .maximumSize(500)
            .expireAfterAccess(Duration.ofMinutes(10)).build();

    @Value("${spring.data.mongodb.database:rgsdb}")
    private String currentDataBase;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) throws Exception {
        if (redisMessageListenerContainer == null || redisTemplate == null) {
            log.info("Redis not configured, skipping message listener setup");
            return;
        }

        // Listen for entity invalidation messages
        MessageListener invalidateListener = (Message message, byte[] pattern) -> {
            try {
                Object object = redisTemplate.getValueSerializer().deserialize(message.getBody());
                log.info("received event {}", object);
                if (object instanceof BaseDto dto) {
                    log.info("received invalidity request for entity {}", dto);
                    invalidateCacheUsingId(dto.getId());
                }
                applicationContext.publishEvent(new EntityUpdateEvent(object));
            } catch (Exception e) {
                log.error("Error processing invalidate message", e);
            }
        };

        // Listen for cache refresh messages
        MessageListener refreshListener = (Message message, byte[] pattern) -> {
            try {
                log.info("received refresh-cache event");
                log.info("cache stats before {}", cachedEntityByIdMap.stats());
                cachedEntityByIdMap.invalidateAll();
                cacheKeyMap.invalidateAll();
                log.info("cache stats after {}", cachedEntityByIdMap.stats());
            } catch (Exception e) {
                log.error("Error processing refresh message", e);
            }
        };

        redisMessageListenerContainer.addMessageListener(invalidateListener,
                new ChannelTopic(INVALIDATE_CACHED_ENTITY));
        redisMessageListenerContainer.addMessageListener(refreshListener, new ChannelTopic("refresh-cache"));

        log.info("Redis message listeners configured for cache invalidation and refresh");
    }

    static class CacheKey {
        String key;

        String id;

        CacheKey(String key) {
            this.key = key;
        }

        public CacheKey(String key, String id) {
            this.key = key;
            this.id = id;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", CacheKey.class.getSimpleName() + "[", "]")
                    .add("key='" + key + "'")
                    .add("id='" + id + "'")
                    .toString();
        }
    }

    @Pointcut("@annotation(aimlabs.gaming.rgs.core.annotations.Cacheable)")
    private void cacheable() {
    }

    @Pointcut("@annotation(aimlabs.gaming.rgs.core.annotations.CacheBust)")
    private void cacheBust() {
    }

    @Around("cacheable()")
    public Object cacheEntities(ProceedingJoinPoint pjp) throws Throwable {
        // Simplified blocking cache implementation
        if (pjp.getArgs().length > 3 && !Arrays.stream(pjp.getArgs()).allMatch(o -> o instanceof String))
            return pjp.proceed();

        if (pjp.getTarget() instanceof AbstractEntityService) {
            AbstractEntityService<?, ?> entityService = (AbstractEntityService<?, ?>) pjp.getTarget();
            MongoEntityStore<?> store = (MongoEntityStore<?>) entityService.getStore();
            org.springframework.data.mongodb.core.mapping.Document document = store.getDocumentClass()
                    .getAnnotation(org.springframework.data.mongodb.core.mapping.Document.class);

            String namespace = currentDataBase + "." + document.collection();
            watchedNameSpaces.add(namespace);

            // TODO: Add tenant context support
            String tenant = "default";
            String key = tenant + "-" + namespace + "-" + pjp.getSignature().getName() + "-" +
                    Arrays.stream(pjp.getArgs()).sequential().map(o -> (String) o).collect(Collectors.joining("-"));

            CacheKey cacheKey = cacheKeyMap.getIfPresent(key);

            if (cacheKey != null && cachedEntityByIdMap.asMap().containsKey(cacheKey.id)) {
                log.info("Served from cache({}).", key);
                return cachedEntityByIdMap.asMap().get(cacheKey.id);
            }

            log.info("Cache Miss. key {}.", key);
            Object result = pjp.proceed();

            if (result instanceof BaseDto) {
                BaseDto dto = (BaseDto) result;
                CacheKey newKey = new CacheKey(key, dto.getId());
                log.info("Cached key {}.", newKey);
                cachedEntityByIdMap.put(dto.getId(), dto);
                cacheKeyMap.put(key, newKey);
                log.info("Cached({}).", key);
            }

            return result;
        } else {
            return pjp.proceed();
        }
    }

    @Around("cacheBust()")
    public Object cacheBust(ProceedingJoinPoint pjp) throws Throwable {
        Object object = pjp.proceed();
        log.info("cacheBust : " + pjp.getSignature());

        if (object instanceof BaseDto) {
            log.info(INVALIDATE_CACHED_ENTITY + " : " + object);
            invalidateCacheUsingId(((BaseDto) object).getId());
        }

        log.info("EntityUpdateEvent : " + object);
        applicationContext.publishEvent(new EntityUpdateEvent(object));
        return object;
    }

    private void invalidateCacheUsingId(String id) {
        cachedEntityByIdMap.invalidate(id);
        Optional<CacheKey> cacheKeyOptional = cacheKeyMap.asMap().values().stream()
                .filter(cacheKey -> cacheKey.id.equals(id))
                .findAny();
        log.info("invalidating cache with key {}", cacheKeyOptional);
        cacheKeyOptional.ifPresent(cacheKey -> cacheKeyMap.invalidate(cacheKey.key));
    }
}
