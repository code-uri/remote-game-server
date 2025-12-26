package aimlabs.gaming.rgs.cache.aspect;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import aimlabs.gaming.rgs.cache.EntityCacheManager;
import aimlabs.gaming.rgs.cache.EntityCacheManager.CacheKey;
import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.entity.BaseDto;
import aimlabs.gaming.rgs.core.event.EntityUpdateEvent;
import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;

@Component
@Aspect
@Slf4j
public class CacheEntitiesImplAspect implements ApplicationContextAware, CommandLineRunner {

    RedisTemplate<String, Object> redisTemplate;
    RedisMessageListenerContainer redisMessageListenerContainer;
    EntityCacheManager entityCacheManager;
    String currentDatabase;

    CacheEntitiesImplAspect(RedisTemplate<String, Object> redisTemplate,
            RedisMessageListenerContainer redisMessageListenerContainer,
            EntityCacheManager entityCacheManager,
         @Value("${spring.data.mongodb.database:rgsdb}") String currentDatabase) {
        this.redisTemplate = redisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
        this.entityCacheManager = entityCacheManager;
        this.currentDatabase = currentDatabase;
    }

    public static final String INVALIDATE_CACHED_ENTITY = "invalidate-cached-entity";

    Set<String> watchedNameSpaces = new HashSet<>();

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
                log.info("cache stats before {}", entityCacheManager.getStats());
                entityCacheManager.getCachedEntityEntries().clear();
                entityCacheManager.getCacheKeyEntries().clear();
                log.info("cache stats after {}", entityCacheManager.getStats());
            } catch (Exception e) {
                log.error("Error processing refresh message", e);
            }
        };

        redisMessageListenerContainer.addMessageListener(invalidateListener,
                new ChannelTopic(INVALIDATE_CACHED_ENTITY));
        redisMessageListenerContainer.addMessageListener(refreshListener, new ChannelTopic("refresh-cache"));

        log.info("Redis message listeners configured for cache invalidation and refresh");
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

            String namespace = currentDatabase + "." + document.collection();
            watchedNameSpaces.add(namespace);

            String tenant = TenantContextHolder.getTenant();
            String key = tenant + "-" + namespace + "-" + pjp.getSignature().getName() + "-" +
                    Arrays.stream(pjp.getArgs()).sequential().map(o -> (String) o).collect(Collectors.joining("-"));

            CacheKey cacheKey = entityCacheManager.getCacheKey(key).orElse(null);

            if (cacheKey != null && entityCacheManager.getCachedEntityEntries().containsKey(cacheKey.id)) {
                log.info("Served from cache({}).", key);
                return entityCacheManager.getCachedEntityEntries().get(cacheKey.id);
            }

            log.info("Cache Miss. key {}.", key);
            Object result = pjp.proceed();

            if (result instanceof BaseDto baseDto) {
                EntityCacheManager.CacheKey newCacheKey = new EntityCacheManager.CacheKey(key, ((BaseDto) result).getId());
                log.info("Cached key {}.", newCacheKey);
                entityCacheManager.putCachedEntity(baseDto.getId(), baseDto);
                entityCacheManager.putCacheKey(key, newCacheKey);
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
         entityCacheManager.invalidateById(id);
    }
}
