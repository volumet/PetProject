package volumet.project.configs;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;
import volumet.project.cache.RedisVersionedCache;
import volumet.project.cache.VersionedCache;
import volumet.project.components.CacheStampedeGuard;
import volumet.project.components.RequestCoalescer;
import volumet.project.configs.properties.DistributedLockProperties;

/**
 * Autoconfiguration (common-resilience): Redis distributed lock and the cache stampede guard.
 * Active only when the app has Redis and spring-integration-redis. The stampede guard uses its own
 * version-ordered Redis store (RedisVersionedCache), not Spring Cache, so it doesn't need @EnableCaching.
 * Settings: volumet.lock.*. Each bean backs off if the app defines its own.
 */
@AutoConfiguration(after = {
        RedisAutoConfiguration.class,
        CacheAutoConfiguration.class,
        RequestCoalescerExecutorConfig.class
})
@ConditionalOnClass({RedisLockRegistry.class, RedisConnectionFactory.class})
@ConditionalOnBean(RedisConnectionFactory.class)
@EnableConfigurationProperties(DistributedLockProperties.class)
public class DistributedLockConfig {

    /**
     * Cluster-wide locks stored in Redis. PUB_SUB_LOCK makes waiting callers get notified on unlock
     * instead of polling Redis.
     */
    @Bean
    @ConditionalOnMissingBean(LockRegistry.class)
    public RedisLockRegistry redisLockRegistry(RedisConnectionFactory connectionFactory,
                                               DistributedLockProperties properties) {
        RedisLockRegistry registry = new RedisLockRegistry(
                connectionFactory,
                properties.getRegistryKey(),
                properties.getExpireAfter().toMillis()
        );
        registry.setRedisLockType(RedisLockRegistry.RedisLockType.PUB_SUB_LOCK);
        return registry;
    }

    /** Version-ordered Redis cache (atomic compare-and-set on the entry's data version). */
    @Bean
    @ConditionalOnMissingBean
    public VersionedCache versionedCache(RedisConnectionFactory connectionFactory) {
        return new RedisVersionedCache(connectionFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheStampedeGuard cacheStampedeGuard(VersionedCache versionedCache,
                                                 LockRegistry lockRegistry,
                                                 RequestCoalescer requestCoalescer,
                                                 DistributedLockProperties properties) {
        return new CacheStampedeGuard(versionedCache, lockRegistry, requestCoalescer, properties);
    }
}
