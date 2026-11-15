package volumet.project.cache;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import volumet.project.keys.KeySpace;
import volumet.project.keys.VersionedKey;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Lua compare-and-set against a real Redis. Skipped automatically when Docker isn't available.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisVersionedCacheTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static final KeySpace ROOMS = KeySpace.of("rooms", 2, Duration.ofMinutes(10));
    private static final KeySpace NO_TTL = KeySpace.of("forever", 1);
    private static final KeySpace SWR = KeySpace.of("swr", 1, Duration.ofMinutes(10)).withStaleWindow(Duration.ofMinutes(5));

    private static LettuceConnectionFactory connectionFactory;
    private static RedisVersionedCache cache;
    private static StringRedisTemplate redis;

    /** A plain API DTO (Jackson needs a no-arg constructor and setters); the version is stored next to it. */
    public static class Room {
        private String name;

        public Room() {
        }

        Room(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getFirstMappedPort()));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        cache = new RedisVersionedCache(connectionFactory);
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void flush() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void storesVersionAndJsonUnderTheVersionedKey() {
        VersionedKey key = ROOMS.key(42);

        assertThat(cache.putIfNewer(key, new Room("A1"), 3L)).isTrue();

        assertThat(redis.opsForHash().get("rooms::v2:42", "v")).isEqualTo("3");
        assertThat((String) redis.opsForHash().get("rooms::v2:42", "d")).contains("\"name\":\"A1\"");
        VersionedCache.Entry<Room> entry = cache.get(key, Room.class);
        assertThat(entry.version()).isEqualTo(3L);
        assertThat(entry.value().getName()).isEqualTo("A1");
        assertThat((String) redis.opsForHash().get("rooms::v2:42", "d")).doesNotContain("version");
    }

    @Test
    void olderOrEqualVersionIsRejected() {
        VersionedKey key = ROOMS.key(1);
        cache.putIfNewer(key, new Room("v3"), 3L);

        assertThat(cache.putIfNewer(key, new Room("v1"), 1L)).isFalse();
        assertThat(cache.putIfNewer(key, new Room("v3 again"), 3L)).isFalse();

        assertThat(cache.get(key, Room.class).value().getName()).isEqualTo("v3");
    }

    @Test
    void newerVersionReplaces() {
        VersionedKey key = ROOMS.key(1);
        cache.putIfNewer(key, new Room("v1"), 1L);

        assertThat(cache.putIfNewer(key, new Room("v2"), 2L)).isTrue();

        assertThat(cache.get(key, Room.class).value().getName()).isEqualTo("v2");
    }

    @Test
    void notFoundAndDeletionMarkersRoundTripAsNull() {
        cache.putIfNewer(ROOMS.key(404), null, VersionedCache.NOT_FOUND_VERSION);
        cache.putIfNewer(ROOMS.key(410), null, VersionedCache.DELETED_VERSION);

        assertThat(cache.get(ROOMS.key(404), Room.class)).isEqualTo(new VersionedCache.Entry<>(-1L, null));
        assertThat(cache.get(ROOMS.key(410), Room.class).version()).isEqualTo(Long.MAX_VALUE);
        assertThat(cache.putIfNewer(ROOMS.key(404), new Room("created"), 0L)).isTrue();
        assertThat(cache.putIfNewer(ROOMS.key(410), new Room("resurrected"), 99L)).isFalse();
    }

    @Test
    void ttlComesFromTheKeySpace() {
        cache.putIfNewer(ROOMS.key(1), new Room("A1"), 1L);
        cache.putIfNewer(NO_TTL.key(1), new Room("A1"), 1L);

        assertThat(redis.getExpire("rooms::v2:1", TimeUnit.SECONDS)).isBetween(1L, 600L);
        assertThat(redis.getExpire("forever::v1:1", TimeUnit.SECONDS)).isEqualTo(-1L);
    }

    @Test
    void staleWindowExtendsStorageAndMarksTheEntryStaleAfterTheTtl() {
        VersionedKey key = SWR.key(1);
        cache.putIfNewer(key, new Room("A1"), 1L);

        // stored for ttl + stale window
        assertThat(redis.getExpire("swr::v1:1", TimeUnit.SECONDS)).isBetween(840L, 900L);
        assertThat(cache.get(key, Room.class).fresh()).isTrue();

        // simulate the TTL running out: only the stale window is left
        redis.expire("swr::v1:1", Duration.ofMinutes(4));
        VersionedCache.Entry<Room> stale = cache.get(key, Room.class);
        assertThat(stale.fresh()).isFalse();
        assertThat(stale.value().getName()).isEqualTo("A1");
    }

    @Test
    void equalVersionRenewsTheTtlButKeepsTheData() {
        VersionedKey key = SWR.key(2);
        cache.putIfNewer(key, new Room("A2"), 3L);
        redis.expire("swr::v1:2", Duration.ofMinutes(4));

        assertThat(cache.putIfNewer(key, new Room("other"), 3L)).isFalse();

        VersionedCache.Entry<Room> entry = cache.get(key, Room.class);
        assertThat(entry.fresh()).isTrue();
        assertThat(entry.value().getName()).isEqualTo("A2");
    }

    @Test
    void absentKeyReturnsNullAndEvictRemoves() {
        assertThat(cache.get(ROOMS.key(7), Room.class)).isNull();

        cache.putIfNewer(ROOMS.key(7), new Room("A7"), 1L);
        cache.evict(ROOMS.key(7));

        assertThat(cache.get(ROOMS.key(7), Room.class)).isNull();
    }

    /** Many concurrent writers with shuffled versions: the highest version always ends up stored. */
    @Test
    void concurrentWritersConvergeOnTheHighestVersion() throws Exception {
        VersionedKey key = ROOMS.key(100);
        List<Long> versions = new ArrayList<>();
        for (long v = 1; v <= 100; v++) {
            versions.add(v);
        }
        Collections.shuffle(versions);
        CountDownLatch go = new CountDownLatch(1);

        try (ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long version : versions) {
                writers.submit(() -> {
                    go.await();
                    return cache.putIfNewer(key, new Room("v" + version), version);
                });
            }
            go.countDown();
        }

        VersionedCache.Entry<Room> entry = cache.get(key, Room.class);
        assertThat(entry.version()).isEqualTo(100L);
        assertThat(entry.value().getName()).isEqualTo("v100");
    }
}
