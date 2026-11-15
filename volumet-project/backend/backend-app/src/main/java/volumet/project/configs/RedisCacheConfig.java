package volumet.project.configs;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring Cache (@Cacheable, e.g. the personUuid cache). The shared default (JSON values,
 * spring.cache.redis.*) comes from common-resilience's RedisCacheDefaultsConfig.
 * <p>
 * Caches read through CacheStampedeGuard (e.g. rooms) don't use Spring Cache: they live in the
 * version-ordered RedisVersionedCache, with their TTL declared on their KeySpace (CacheKeySpaces).
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {
}
