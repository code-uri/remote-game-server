package aimlabs.gaming.rgs.cache;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import aimlabs.gaming.rgs.core.entity.BaseDto;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

@Component
public class EntityCacheManager {

    private final Cache<String, BaseDto> cachedEntityByIdMap;
    private final Cache<String, CacheKey> cacheKeyMap;

    public EntityCacheManager() {
        this.cachedEntityByIdMap = Caffeine.newBuilder()
                .initialCapacity(100)
                .expireAfterAccess(Duration.ofMinutes(10))
                .recordStats()
                .build();
        this.cacheKeyMap = Caffeine.newBuilder()
                .initialCapacity(100)
                .expireAfterAccess(Duration.ofMinutes(10))
                .recordStats()
                .build();
    }

    public Optional<BaseDto> getCachedEntity(String id) {
        return Optional.ofNullable(cachedEntityByIdMap.getIfPresent(id));
    }

    public void putCachedEntity(String id, BaseDto dto) {
        if (id != null && dto != null) cachedEntityByIdMap.put(id, dto);
    }

    public Map<String, BaseDto> getCachedEntityEntries() {
        return cachedEntityByIdMap.asMap();
    }

    public Optional<CacheKey> getCacheKey(String key) {
        return Optional.ofNullable(cacheKeyMap.getIfPresent(key));
    }

    public void putCacheKey(String key, CacheKey cacheKey) {
        if (key != null && cacheKey != null) cacheKeyMap.put(key, cacheKey);
    }

    public Map<String, CacheKey> getCacheKeyEntries() {
        return cacheKeyMap.asMap();
    }

    public Optional<CacheKey> findCacheKeyById(String id) {
        return cacheKeyMap.asMap().values().stream().filter(k -> id.equals(k.id)).findAny();
    }

    public void invalidateById(String id) {
        cachedEntityByIdMap.invalidate(id);
        findCacheKeyById(id).ifPresent(k -> cacheKeyMap.invalidate(k.key));
    }

    public void invalidateCacheKey(String key) {
        cacheKeyMap.invalidate(key);
    }

    public CacheStats getStats() {
        return cachedEntityByIdMap.stats();
    }

    public static class CacheKey {
        String key;
        public String id;

        public CacheKey(String key) { this.key = key; }

        public CacheKey(String key, String id) { this.key = key; this.id = id; }

        @Override
        public String toString() {
            return new StringJoiner(", ", CacheKey.class.getSimpleName() + "[", "]")
                    .add("key='" + key + "'")
                    .add("id='" + id + "'")
                    .toString();
        }
    }
}
