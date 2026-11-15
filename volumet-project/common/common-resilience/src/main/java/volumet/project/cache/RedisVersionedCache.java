package volumet.project.cache;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import volumet.project.keys.KeySpace;
import volumet.project.keys.VersionedKey;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static volumet.project.constants.StringConstants.DOUBLE_COLON_SEPARATOR;
import static volumet.project.constants.StringConstants.EMPTY;

/**
 * {@link VersionedCache} on Redis. Each entry is a hash at {@code <space>::<cacheKey>} (e.g. {@code rooms::v2:42})
 * with fields {@code v} (data version) and {@code d} (JSON value, empty for "not found"/deleted).
 * <p>
 * Both operations are Lua scripts, which Redis executes atomically:
 * <ul>
 *   <li>{@value #PUT_IF_NEWER_SCRIPT}: compare-and-set on the data version; no other command (from any
 *       instance) runs between reading the stored version and writing the new one.</li>
 *   <li>{@value #GET_ENTRY_SCRIPT}: the entry plus its remaining TTL. The entry lives for
 *       {@link KeySpace#storageTtl()} (ttl + stale window) and is stale once the remaining TTL is within the
 *       stale window, so freshness follows Redis's clock, not the pods' clocks.</li>
 * </ul>
 */
public class RedisVersionedCache implements VersionedCache {
    /** Hash field names; the Lua scripts use the same ones. */
    static final String VERSION_FIELD = "v";
    static final String DATA_FIELD = "d";

    static final String PUT_IF_NEWER_SCRIPT = "volumet/redis/put-if-newer.lua";
    static final String GET_ENTRY_SCRIPT = "volumet/redis/get-entry.lua";

    /** PTTL of a key without expiry. */
    private static final long NO_EXPIRY = -1L;

    /**
     * Loaded once from the classpath; Spring runs it by SHA (EVALSHA) and only sends the source when Redis
     * hasn't cached it yet. Returns 1 if written, 0 if the stored version is equal (TTL renewed) or newer.
     */
    static final RedisScript<Long> PUT_IF_NEWER = script(PUT_IF_NEWER_SCRIPT, Long.class);

    /** Returns {@code []} if absent, else {@code [version, json, pttl]} (all strings). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static final RedisScript<List<String>> GET_ENTRY = (RedisScript) script(GET_ENTRY_SCRIPT, List.class);

    private final StringRedisTemplate redis;
    private final GenericJackson2JsonRedisSerializer serializer;

    public RedisVersionedCache(RedisConnectionFactory connectionFactory) {
        this(connectionFactory, new GenericJackson2JsonRedisSerializer());
    }

    public RedisVersionedCache(RedisConnectionFactory connectionFactory, GenericJackson2JsonRedisSerializer serializer) {
        this.redis = new StringRedisTemplate(connectionFactory);
        this.redis.afterPropertiesSet();
        this.serializer = serializer;
    }

    @Override
    public <T> Entry<T> get(VersionedKey key, Class<T> type) {
        List<String> fields = redis.execute(GET_ENTRY, List.of(redisKey(key)));
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        long version = Long.parseLong(fields.get(0));
        String data = fields.get(1);
        T value = data == null || data.isEmpty()
                ? null
                : serializer.deserialize(data.getBytes(StandardCharsets.UTF_8), type);
        return new Entry<>(version, value, isFresh(key.space(), Long.parseLong(fields.get(2))));
    }

    @Override
    public boolean putIfNewer(VersionedKey key, Object value, long version) {
        String data = value == null ? EMPTY : new String(Objects.requireNonNull(serializer.serialize(value)), StandardCharsets.UTF_8);
        Duration storageTtl = key.space().storageTtl();
        Long written = redis.execute(PUT_IF_NEWER, List.of(redisKey(key)),
                Long.toString(version), data, Long.toString(storageTtl == null ? 0 : storageTtl.toMillis()));
        return written != null && written == 1L;
    }

    @Override
    public void evict(VersionedKey key) {
        redis.delete(redisKey(key));
    }

    /** Same layout as Spring's RedisCache keys: {@code <cache name>::<key>}. */
    static String redisKey(VersionedKey key) {
        return key.space().name() + DOUBLE_COLON_SEPARATOR + key.cacheKey();
    }

    /** Fresh while more than the stale window is left of the storage TTL (always, without expiry). */
    static boolean isFresh(KeySpace space, long remainingMillis) {
        return remainingMillis == NO_EXPIRY || remainingMillis > space.staleWindow().toMillis();
    }

    private static <R> RedisScript<R> script(String path, Class<R> resultType) {
        return RedisScript.of(new ClassPathResource(path, RedisVersionedCache.class.getClassLoader()), resultType);
    }
}
