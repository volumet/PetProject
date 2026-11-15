package volumet.project.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Lua script now lives in a classpath file; this guards that it is packaged and loads (no Docker needed).
 * Its behaviour against real Redis is covered by RedisVersionedCacheTest.
 */
class PutIfNewerScriptTest {

    @Test
    void scriptLoadsFromTheClasspath() {
        assertThat(RedisVersionedCache.PUT_IF_NEWER.getScriptAsString())
                .contains("redis.call('HGET', key, 'v')")
                .contains("redis.call('HSET', key, 'v', ARGV[1], 'd', value)");
        assertThat(RedisVersionedCache.PUT_IF_NEWER.getResultType()).isEqualTo(Long.class);
        // Spring runs it by SHA (EVALSHA) after the first call
        assertThat(RedisVersionedCache.PUT_IF_NEWER.getSha1()).hasSize(40);
    }

    @Test
    void scriptUsesTheSameHashFieldsAsTheJavaSide() {
        for (String script : new String[]{
                RedisVersionedCache.PUT_IF_NEWER.getScriptAsString(),
                RedisVersionedCache.GET_ENTRY.getScriptAsString()}) {
            assertThat(script).contains("'" + RedisVersionedCache.VERSION_FIELD + "'", "'" + RedisVersionedCache.DATA_FIELD + "'");
        }
    }

    @Test
    void readScriptLoadsFromTheClasspath() {
        assertThat(RedisVersionedCache.GET_ENTRY.getScriptAsString()).contains("redis.call('PTTL', KEYS[1])");
        assertThat(RedisVersionedCache.GET_ENTRY.getResultType()).isEqualTo(java.util.List.class);
    }

    @Test
    void freshnessFollowsTheRemainingTtl() {
        var space = volumet.project.keys.KeySpace.of("rooms", 1, java.time.Duration.ofMinutes(10))
                .withStaleWindow(java.time.Duration.ofMinutes(5));

        assertThat(RedisVersionedCache.isFresh(space, 300_001)).isTrue();
        assertThat(RedisVersionedCache.isFresh(space, 300_000)).isFalse();
        assertThat(RedisVersionedCache.isFresh(space, -1)).isTrue(); // no expiry
        assertThat(RedisVersionedCache.isFresh(volumet.project.keys.KeySpace.of("rooms", 1, java.time.Duration.ofMinutes(10)), 1)).isTrue();
    }
}
