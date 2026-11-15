package volumet.project.configs;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Auto-configuration (common-resilience): shared default for every Redis cache in every service.
 * Values are stored as JSON with type info, so any service (and a human with redis-cli) can read them.
 * <p>
 * Defining a RedisCacheConfiguration bean makes Spring Boot ignore spring.cache.redis.*, so those
 * settings (time-to-live, key-prefix, use-key-prefix, cache-null-values) are applied here to keep working.
 * Per-cache settings (e.g. a TTL for one cache) belong in the app, via RedisCacheManagerBuilderCustomizer.
 * Caching itself stays opt-in: the app enables it with @EnableCaching.
 */
@AutoConfiguration(before = CacheAutoConfiguration.class)
@ConditionalOnClass({RedisCacheConfiguration.class, GenericJackson2JsonRedisSerializer.class})
@EnableConfigurationProperties(CacheProperties.class)
public class RedisCacheDefaultsConfig {

    @Bean
    @ConditionalOnMissingBean
    public RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())
                );

        CacheProperties.Redis redis = cacheProperties.getRedis();
        if (redis.getTimeToLive() != null) {
            config = config.entryTtl(redis.getTimeToLive());
        }
        if (redis.getKeyPrefix() != null) {
            config = config.prefixCacheNameWith(redis.getKeyPrefix());
        }
        if (!redis.isCacheNullValues()) {
            // note: CacheStampedeGuard then can't cache "not found" results, so missing ids reach the loader
            config = config.disableCachingNullValues();
        }
        if (!redis.isUseKeyPrefix()) {
            config = config.disableKeyPrefix();
        }
        return config;
    }
}
