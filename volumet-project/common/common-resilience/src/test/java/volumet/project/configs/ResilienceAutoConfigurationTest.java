package volumet.project.configs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;
import volumet.project.cache.RedisVersionedCache;
import volumet.project.cache.VersionedCache;
import volumet.project.components.CacheStampedeGuard;
import volumet.project.components.RequestCoalescer;
import volumet.project.configs.properties.RequestCoalescerExecutorProperties;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static volumet.project.configs.RequestCoalescerExecutorConfig.REQUEST_COALESCER_TASK_EXECUTOR;

/**
 * Verifies what an app gets just by adding common-resilience, and that each piece backs off correctly.
 * A mock RedisConnectionFactory stands in for the app's Redis (nothing connects during these tests).
 */
class ResilienceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ValidationAutoConfiguration.class,
                    CacheAutoConfiguration.class,
                    RequestCoalescerExecutorConfig.class,
                    DistributedLockConfig.class,
                    RedisCacheDefaultsConfig.class))
            .withPropertyValues("spring.cache.type=simple");

    @Configuration(proxyBeanMethods = false)
    static class RedisAvailable {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CachingEnabled {
    }

    @Test
    void appWithRedisGetsEverything() {
        runner.withUserConfiguration(RedisAvailable.class, CachingEnabled.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RequestCoalescer.class);
            assertThat(context).hasBean(REQUEST_COALESCER_TASK_EXECUTOR);
            assertThat(context).hasSingleBean(RedisLockRegistry.class);
            assertThat(context.getBean(VersionedCache.class)).isInstanceOf(RedisVersionedCache.class);
            assertThat(context).hasSingleBean(CacheStampedeGuard.class);
        });
    }

    @Test
    void stampedeGuardDoesNotNeedSpringCaching() {
        // the guard uses its own version-ordered store, not Spring Cache
        runner.withUserConfiguration(RedisAvailable.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(VersionedCache.class);
            assertThat(context).hasSingleBean(CacheStampedeGuard.class);
        });
    }

    @Test
    void appDefinedVersionedCacheWins() {
        VersionedCache own = mock(VersionedCache.class);

        runner.withUserConfiguration(RedisAvailable.class)
                .withBean(VersionedCache.class, () -> own)
                .run(context -> assertThat(context.getBean(VersionedCache.class)).isSameAs(own));
    }

    @Test
    void withoutRedisOnlyTheCoalescerIsConfigured() {
        runner.withUserConfiguration(CachingEnabled.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RequestCoalescer.class);
            assertThat(context).doesNotHaveBean(LockRegistry.class);
            assertThat(context).doesNotHaveBean(VersionedCache.class);
            assertThat(context).doesNotHaveBean(CacheStampedeGuard.class);
        });
    }

    @Test
    void withoutSpringIntegrationRedisTheLockIsNotConfigured() {
        runner.withUserConfiguration(RedisAvailable.class, CachingEnabled.class)
                .withClassLoader(new FilteredClassLoader(RedisLockRegistry.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RequestCoalescer.class);
                    assertThat(context).doesNotHaveBean(LockRegistry.class);
                    assertThat(context).doesNotHaveBean(CacheStampedeGuard.class);
                });
    }

    @Test
    void appDefinedBeansWin() {
        RequestCoalescer own = new RequestCoalescer(Runnable::run, new RequestCoalescerExecutorProperties());
        LockRegistry ownLocks = mock(LockRegistry.class);

        runner.withUserConfiguration(RedisAvailable.class, CachingEnabled.class)
                .withBean(RequestCoalescer.class, () -> own)
                .withBean(LockRegistry.class, () -> ownLocks)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RequestCoalescer.class)).isSameAs(own);
                    assertThat(context.getBean(LockRegistry.class)).isSameAs(ownLocks);
                    assertThat(context).doesNotHaveBean(RedisLockRegistry.class);
                });
    }

    @Test
    void propertiesAreBound() {
        runner.withPropertyValues(
                        "volumet.executor.request-coalescer.concurrency-limit=8",
                        "volumet.executor.request-coalescer.default-timeout=2s")
                .run(context -> {
                    RequestCoalescerExecutorProperties properties = context.getBean(RequestCoalescerExecutorProperties.class);
                    assertThat(properties.getConcurrencyLimit()).isEqualTo(8);
                    assertThat(properties.getDefaultTimeout()).isEqualTo(Duration.ofSeconds(2));
                });
    }

    @Test
    void zeroConcurrencyLimitFailsStartup() {
        runner.withPropertyValues("volumet.executor.request-coalescer.concurrency-limit=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void lockWaitLongerThanLeaseFailsStartup() {
        runner.withUserConfiguration(RedisAvailable.class)
                .withPropertyValues("volumet.lock.wait-time=60s", "volumet.lock.expire-after=30s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void blankLockRegistryKeyFailsStartup() {
        runner.withUserConfiguration(RedisAvailable.class)
                .withPropertyValues("volumet.lock.registry-key= ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void redisCacheDefaultStoresJson() {
        runner.run(context -> {
            RedisCacheConfiguration config = context.getBean(RedisCacheConfiguration.class);
            ByteBuffer bytes = config.getValueSerializationPair().write(Map.of("roomName", "A1"));

            assertThat(StandardCharsets.UTF_8.decode(bytes).toString()).contains("\"roomName\":\"A1\"");
            assertThat(config.getAllowCacheNullValues()).isTrue();
        });
    }

    @Test
    void redisCacheDefaultHonoursSpringCacheRedisProperties() {
        runner.withPropertyValues(
                        "spring.cache.redis.time-to-live=5m",
                        "spring.cache.redis.key-prefix=svc:",
                        "spring.cache.redis.cache-null-values=false")
                .run(context -> {
                    RedisCacheConfiguration config = context.getBean(RedisCacheConfiguration.class);
                    assertThat(config.getTtlFunction().getTimeToLive(null, null)).isEqualTo(Duration.ofMinutes(5));
                    assertThat(config.getKeyPrefixFor("rooms")).isEqualTo("svc:rooms::");
                    assertThat(config.getAllowCacheNullValues()).isFalse();
                });
    }

    @Test
    void appDefinedRedisCacheConfigurationWins() {
        RedisCacheConfiguration own = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(1));

        runner.withBean(RedisCacheConfiguration.class, () -> own)
                .run(context -> assertThat(context.getBean(RedisCacheConfiguration.class)).isSameAs(own));
    }
}
