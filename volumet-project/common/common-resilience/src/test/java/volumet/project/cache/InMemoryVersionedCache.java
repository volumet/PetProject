package volumet.project.cache;

import volumet.project.keys.VersionedKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test double with the same compare-and-set semantics as {@link RedisVersionedCache}'s Lua script
 * (an entry is replaced only by a strictly higher version; an equal version makes it fresh again). Shared between
 * "instances" in tests the way Redis is shared between pods. TTLs are not simulated: {@link #makeStale} moves an
 * entry into its stale window.
 */
public class InMemoryVersionedCache implements VersionedCache {
    private final Map<String, Entry<Object>> entries = new ConcurrentHashMap<>();

    @Override
    public <T> Entry<T> get(VersionedKey key, Class<T> type) {
        Entry<Object> entry = entries.get(RedisVersionedCache.redisKey(key));
        return entry == null ? null : new Entry<>(entry.version(), type.cast(entry.value()), entry.fresh());
    }

    @Override
    public boolean putIfNewer(VersionedKey key, Object value, long version) {
        boolean[] written = {false};
        entries.compute(RedisVersionedCache.redisKey(key), (k, current) -> {
            if (current != null && current.version() > version) {
                return current;
            }
            if (current != null && current.version() == version) {
                return new Entry<>(current.version(), current.value()); // TTL renewed: fresh again
            }
            written[0] = true;
            return new Entry<>(version, value);
        });
        return written[0];
    }

    @Override
    public void evict(VersionedKey key) {
        entries.remove(RedisVersionedCache.redisKey(key));
    }

    /** Simulates the TTL running out while the stale window keeps the entry readable. */
    public void makeStale(VersionedKey key) {
        entries.computeIfPresent(RedisVersionedCache.redisKey(key),
                (k, current) -> new Entry<>(current.version(), current.value(), false));
    }

    /** Stored version, for assertions; null if absent. */
    public Long storedVersion(VersionedKey key) {
        Entry<Object> entry = entries.get(RedisVersionedCache.redisKey(key));
        return entry == null ? null : entry.version();
    }
}
