package aimlabs.gaming.rgs.cache.aspect;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.entity.BaseDto;
import aimlabs.gaming.rgs.core.event.EntityUpdateEvent;
import com.github.benmanes.caffeine.cache.Cache;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheEntitiesImplAspect Tests")
class CacheEntitiesImplAspectTest {

    @Mock
    private ProceedingJoinPoint pjp;

    @Mock
    private Signature signature;

    @Mock
    private AbstractEntityService<BaseDto, String> entityService;

    @Mock
    private MongoEntityStore<BaseDto> store;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private RedisMessageListenerContainer listenerContainer;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisSerializer<Object> valueSerializer;

    private CacheEntitiesImplAspect aspect;

    private TestDto testDto;

    @BeforeEach
    void setUp() {
        aspect = new CacheEntitiesImplAspect();
        aspect.setApplicationContext(applicationContext);
        ReflectionTestUtils.setField(aspect, "currentDataBase", "testdb");
        ReflectionTestUtils.setField(aspect, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(aspect, "redisMessageListenerContainer", listenerContainer);

        testDto = new TestDto("test-id-123", "Test Entity");
    }

    @Test
    @DisplayName("Should cache result on first call and return from cache on second call")
    void testCacheHit_ShouldReturnCachedValue() throws Throwable {
        // Given
        when(pjp.getTarget()).thenReturn(entityService);
        when(pjp.getArgs()).thenReturn(new Object[] { "param1" });
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("findById");
        when(entityService.getStore()).thenReturn(store);
        when(store.getDocumentClass()).thenReturn((Class) TestDto.class);
        when(pjp.proceed()).thenReturn(testDto);

        // When - First call (cache miss)
        Object result1 = aspect.cacheEntities(pjp);

        // Then - Verify method was called
        verify(pjp, times(1)).proceed();
        assertEquals(testDto, result1);

        // When - Second call (cache hit)
        Object result2 = aspect.cacheEntities(pjp);

        // Then - Method should not be called again
        verify(pjp, times(1)).proceed();
        assertEquals(testDto, result2);
        assertSame(result1, result2);
    }

    @Test
    @DisplayName("Should bypass cache when target is not AbstractEntityService")
    void testCacheEntities_ShouldBypassWhenNotEntityService() throws Throwable {
        // Given
        Object nonServiceTarget = new Object();
        when(pjp.getTarget()).thenReturn(nonServiceTarget);
        when(pjp.proceed()).thenReturn("result");

        // When
        Object result = aspect.cacheEntities(pjp);

        // Then
        verify(pjp, times(1)).proceed();
        assertEquals("result", result);
    }

    @Test
    @DisplayName("Should bypass cache when args length > 3")
    void testCacheEntities_ShouldBypassWhenTooManyArgs() throws Throwable {
        // Given
        when(pjp.getArgs()).thenReturn(new Object[] { "arg1", "arg2", "arg3", "arg4" });
        when(pjp.proceed()).thenReturn("result");

        // When
        Object result = aspect.cacheEntities(pjp);

        // Then
        verify(pjp, times(1)).proceed();
        assertEquals("result", result);
    }

    @Test
    @DisplayName("Should bypass cache when args are not all Strings")
    void testCacheEntities_ShouldBypassWhenNonStringArgs() throws Throwable {
        // Given
        when(pjp.getArgs()).thenReturn(new Object[] { "arg1", 123 });
        when(pjp.proceed()).thenReturn("result");

        // When
        Object result = aspect.cacheEntities(pjp);

        // Then
        verify(pjp, times(1)).proceed();
        assertEquals("result", result);
    }

    @Test
    @DisplayName("Should not cache non-BaseDto results")
    void testCacheEntities_ShouldNotCacheNonBaseDto() throws Throwable {
        // Given
        when(pjp.getTarget()).thenReturn(entityService);
        when(pjp.getArgs()).thenReturn(new Object[] { "param1" });
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("someMethod");
        when(entityService.getStore()).thenReturn(store);
        when(store.getDocumentClass()).thenReturn((Class) TestDto.class);
        when(pjp.proceed()).thenReturn("non-dto-result");

        // When
        Object result = aspect.cacheEntities(pjp);

        // Then
        assertEquals("non-dto-result", result);
        verify(pjp, times(1)).proceed();

        // Verify cache is empty
        Cache<String, BaseDto> cache = (Cache<String, BaseDto>) ReflectionTestUtils.getField(aspect,
                "cachedEntityByIdMap");
        assertEquals(0, cache.asMap().size());
    }

    @Test
    @DisplayName("Should invalidate cache on cacheBust")
    void testCacheBust_ShouldInvalidateCache() throws Throwable {
        // Given - First populate the cache
        when(pjp.getTarget()).thenReturn(entityService);
        when(pjp.getArgs()).thenReturn(new Object[] { "param1" });
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("findById");
        when(entityService.getStore()).thenReturn(store);
        when(store.getDocumentClass()).thenReturn((Class) TestDto.class);
        when(pjp.proceed()).thenReturn(testDto);

        aspect.cacheEntities(pjp);

        // Verify cache has entry
        Cache<String, BaseDto> cache = (Cache<String, BaseDto>) ReflectionTestUtils.getField(aspect,
                "cachedEntityByIdMap");
        assertEquals(1, cache.asMap().size());

        // When - Cache bust
        when(pjp.proceed()).thenReturn(testDto);
        aspect.cacheBust(pjp);

        // Then - Cache should be invalidated
        assertEquals(0, cache.asMap().size());
        verify(applicationContext, times(1)).publishEvent(any(EntityUpdateEvent.class));
    }

    @Test
    @DisplayName("Should publish EntityUpdateEvent on cacheBust")
    void testCacheBust_ShouldPublishEvent() throws Throwable {
        // Given
        when(pjp.proceed()).thenReturn(testDto);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.toString()).thenReturn("TestSignature");

        ArgumentCaptor<EntityUpdateEvent> eventCaptor = ArgumentCaptor.forClass(EntityUpdateEvent.class);

        // When
        aspect.cacheBust(pjp);

        // Then
        verify(applicationContext, times(1)).publishEvent(eventCaptor.capture());
        EntityUpdateEvent event = eventCaptor.getValue();
        assertEquals(testDto, event.getSource());
    }

    @Test
    @DisplayName("Should invalidate cache by ID")
    void testInvalidateCacheUsingId() throws Throwable {
        // Given - Populate cache
        when(pjp.getTarget()).thenReturn(entityService);
        when(pjp.getArgs()).thenReturn(new Object[] { "param1" });
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("findById");
        when(entityService.getStore()).thenReturn(store);
        when(store.getDocumentClass()).thenReturn((Class) TestDto.class);
        when(pjp.proceed()).thenReturn(testDto);

        aspect.cacheEntities(pjp);

        Cache<String, BaseDto> cache = (Cache<String, BaseDto>) ReflectionTestUtils.getField(aspect,
                "cachedEntityByIdMap");
        assertEquals(1, cache.asMap().size());

        // When - Invalidate using private method via reflection
        aspect.cacheBust(pjp);

        // Then
        assertEquals(0, cache.asMap().size());
    }

    @Test
    @DisplayName("Should skip Redis listener setup when Redis not configured")
    void testRun_ShouldSkipWhenRedisNotConfigured() throws Exception {
        // Given
        ReflectionTestUtils.setField(aspect, "redisMessageListenerContainer", null);

        // When
        aspect.run();

        // Then
        verify(listenerContainer, never()).addMessageListener(any(), any());
    }

    @Test
    @DisplayName("Should setup Redis listeners when configured")
    void testRun_ShouldSetupListeners() throws Exception {
        // Given
        when(redisTemplate.getValueSerializer()).thenReturn(valueSerializer);

        // When
        aspect.run();

        // Then
        verify(listenerContainer, times(2)).addMessageListener(any(), any(ChannelTopic.class));
    }

    @Test
    @DisplayName("Should handle invalidate message and invalidate cache")
    void testRedisInvalidateListener_ShouldInvalidateCache() throws Exception {
        // Given - Populate cache first
        when(pjp.getTarget()).thenReturn(entityService);
        when(pjp.getArgs()).thenReturn(new Object[] { "param1" });
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("findById");
        when(entityService.getStore()).thenReturn(store);
        when(store.getDocumentClass()).thenReturn((Class) TestDto.class);
        when(pjp.proceed()).thenReturn(testDto);

        aspect.cacheEntities(pjp);

        Cache<String, BaseDto> cache = (Cache<String, BaseDto>) ReflectionTestUtils.getField(aspect,
                "cachedEntityByIdMap");
        assertEquals(1, cache.asMap().size());

        // Setup Redis listener
        when(redisTemplate.getValueSerializer()).thenReturn(valueSerializer);
        ArgumentCaptor<org.springframework.data.redis.connection.MessageListener> listenerCaptor = ArgumentCaptor
                .forClass(org.springframework.data.redis.connection.MessageListener.class);

        aspect.run();

        verify(listenerContainer, times(2)).addMessageListener(listenerCaptor.capture(), any(ChannelTopic.class));

        // When - Simulate Redis message
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn("message-body".getBytes());
        when(valueSerializer.deserialize(any())).thenReturn(testDto);

        listenerCaptor.getAllValues().get(0).onMessage(message, null);

        // Then
        assertEquals(0, cache.asMap().size());
        verify(applicationContext, times(1)).publishEvent(any(EntityUpdateEvent.class));
    }

    @Test
    @DisplayName("Should handle refresh message and clear all caches")
    void testRedisRefreshListener_ShouldClearAllCaches() throws Exception {
        // Given - Populate cache
        when(pjp.getTarget()).thenReturn(entityService);
        when(pjp.getArgs()).thenReturn(new Object[] { "param1" });
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("findById");
        when(entityService.getStore()).thenReturn(store);
        when(store.getDocumentClass()).thenReturn((Class) TestDto.class);
        when(pjp.proceed()).thenReturn(testDto);

        aspect.cacheEntities(pjp);

        Cache<String, BaseDto> cache = (Cache<String, BaseDto>) ReflectionTestUtils.getField(aspect,
                "cachedEntityByIdMap");
        assertEquals(1, cache.asMap().size());

        // Setup Redis listener
        when(redisTemplate.getValueSerializer()).thenReturn(valueSerializer);
        ArgumentCaptor<org.springframework.data.redis.connection.MessageListener> listenerCaptor = ArgumentCaptor
                .forClass(org.springframework.data.redis.connection.MessageListener.class);

        aspect.run();

        verify(listenerContainer, times(2)).addMessageListener(listenerCaptor.capture(), any(ChannelTopic.class));

        // When - Simulate refresh message
        Message message = mock(Message.class);
        listenerCaptor.getAllValues().get(1).onMessage(message, null);

        // Then
        assertEquals(0, cache.asMap().size());
    }

    @Test
    @DisplayName("Should generate correct cache key with parameters")
    void testCacheKeyGeneration() throws Throwable {
        // Given
        when(pjp.getTarget()).thenReturn(entityService);
        when(pjp.getArgs()).thenReturn(new Object[] { "param1", "param2" });
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("findByTwoParams");
        when(entityService.getStore()).thenReturn(store);
        when(store.getDocumentClass()).thenReturn((Class) TestDto.class);
        when(pjp.proceed()).thenReturn(testDto);

        // When
        aspect.cacheEntities(pjp);

        // Then
        Cache<String, CacheEntitiesImplAspect.CacheKey> cacheKeyMap = (Cache<String, CacheEntitiesImplAspect.CacheKey>) ReflectionTestUtils
                .getField(aspect, "cacheKeyMap");

        assertEquals(1, cacheKeyMap.asMap().size());
        String expectedKeyPattern = "default-testdb.test_collection-findByTwoParams-param1-param2";
        assertTrue(cacheKeyMap.asMap().containsKey(expectedKeyPattern));
    }

    @Test
    @DisplayName("CacheKey toString should format correctly")
    void testCacheKeyToString() {
        // Given
        CacheEntitiesImplAspect.CacheKey cacheKey = new CacheEntitiesImplAspect.CacheKey("test-key", "test-id");

        // When
        String result = cacheKey.toString();

        // Then
        assertTrue(result.contains("key='test-key'"));
        assertTrue(result.contains("id='test-id'"));
    }

    @Test
    @DisplayName("Should handle concurrent cache access")
    void testConcurrentCacheAccess() throws Throwable {
        // Given
        when(pjp.getTarget()).thenReturn(entityService);
        when(pjp.getArgs()).thenReturn(new Object[] { "param1" });
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("findById");
        when(entityService.getStore()).thenReturn(store);
        when(store.getDocumentClass()).thenReturn((Class) TestDto.class);
        when(pjp.proceed()).thenReturn(testDto);

        // When - Multiple concurrent calls
        Object result1 = aspect.cacheEntities(pjp);
        Object result2 = aspect.cacheEntities(pjp);
        Object result3 = aspect.cacheEntities(pjp);

        // Then - Only one actual method call
        verify(pjp, times(1)).proceed();
        assertSame(result1, result2);
        assertSame(result2, result3);
    }

    // Test DTO class
    @Document(collection = "test_collection")
    static class TestDto extends BaseDto {
        private String name;

        public TestDto(String id, String name) {
            this.setId(id);
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
