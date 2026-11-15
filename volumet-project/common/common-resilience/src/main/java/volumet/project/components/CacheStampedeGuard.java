package volumet.project.components;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.support.locks.LockRegistry;
import volumet.project.cache.CacheReadPolicy;
import volumet.project.cache.VersionedCache;
import volumet.project.cache.VersionedValue;
import volumet.project.configs.properties.DistributedLockProperties;
import volumet.project.keys.VersionedKey;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * Read-through, version-ordered cache protected against stampedes:
 * <ol>
 *   <li><b>Redis cache</b>: a fresh hit returns immediately. "Not found" is cached too, so lookups of missing ids
 *       don't reach the DB either.</li>
 *   <li><b>Request coalescer</b>: on a miss, concurrent callers on this instance share one load.</li>
 *   <li><b>Redis distributed lock</b>: across instances, only the lock holder loads; the others wait,
 *       then find the value in the cache (re-checked after the lock).</li>
 *   <li>The loader itself should be protected with resilience4j (bulkhead / circuit breaker / retry).</li>
 * </ol>
 * <b>Stale entries</b> (past the key space's TTL, inside its stale window) depend on the {@link CacheReadPolicy}:
 * <ul>
 *   <li>{@link CacheReadPolicy#FRESH_ONLY} (default): a miss; the caller waits for the reload.</li>
 *   <li>{@link CacheReadPolicy#STALE_WHILE_REVALIDATE}: returned at once while one background reload (same
 *       coalescer and lock as a miss) refreshes the entry.</li>
 * </ul>
 * <b>Ordered by data version</b>: every write goes through {@link VersionedCache#putIfNewer}, so a load that
 * read version 1 from the DB can never overwrite version 3 that an update stored in the meantime. When that
 * happens the newer stored value is returned instead of the stale one. A reload that finds the same version
 * renews the entry's TTL, which makes it fresh again.
 * <p>
 * Degrades instead of failing: if Redis is down, or the lock can't be acquired within the wait time,
 * the value is loaded directly (still behind the coalescer and the loader's resilience4j guards).
 * <p>
 * Registered by {@link volumet.project.configs.DistributedLockConfig} (autoconfiguration) when the app has Redis.
 */
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CacheStampedeGuard {
    VersionedCache cache;
    LockRegistry lockRegistry;
    RequestCoalescer requestCoalescer;
    DistributedLockProperties lockProperties;

    /** {@link #get(VersionedKey, Class, CacheReadPolicy, Supplier)} with {@link CacheReadPolicy#FRESH_ONLY}. */
    public <T> T get(VersionedKey key, Class<T> type, Supplier<VersionedValue<T>> loader) {
        return get(key, type, CacheReadPolicy.FRESH_ONLY, loader);
    }

    /**
     * One key drives all three layers:
     * <ul>
     *   <li>cache: Redis {@code rooms::v2:42}</li>
     *   <li>lock: {@code key.qualified()} → Redis {@code <volumet.lock.registry-key>:rooms:v2:42}</li>
     *   <li>coalescer: the key itself</li>
     * </ul>
     *
     * @param policy whether a stale entry may be returned while it is reloaded in the background
     * @param loader returns the value with its data version (read in the same query), or {@code null} if not found.
     *               Under {@code STALE_WHILE_REVALIDATE} it may run on a background thread after this call returned,
     *               so it must not depend on the caller's thread (SecurityContext, transaction).
     * @return the cached or loaded value (without its version); null if not found
     */
    public <T> T get(VersionedKey key, Class<T> type, CacheReadPolicy policy, Supplier<VersionedValue<T>> loader) {
        Objects.requireNonNull(policy, "policy");
        VersionedCache.Entry<T> cached = read(key, type);
        if (cached != null) {
            if (cached.fresh()) {
                return cached.value();
            }
            if (policy == CacheReadPolicy.STALE_WHILE_REVALIDATE) {
                revalidate(key, type, loader);
                return cached.value();
            }
        }
        return requestCoalescer.execute(key, () -> loadUnderLock(key, type, loader));
    }

    /**
     * Write path (after an update/insert commits): stores {@code value} under {@code version} (the entity's new
     * {@code @Version}) unless the cache already holds an equal or newer version.
     *
     * @return true if stored, false if an equal or newer version was already cached (or the cache is unavailable)
     */
    public boolean put(VersionedKey key, Object value, long version) {
        Objects.requireNonNull(value, "value");
        return write(key, value, version);
    }

    /**
     * Delete path: stores a deletion marker that no older load can overwrite (a plain evict could be
     * undone by a load that read the row just before the delete). Reads return null until the marker expires
     * with the key space's storage TTL. Without a TTL the marker stays, so only use it for ids that are never reused.
     */
    public void markDeleted(VersionedKey key) {
        write(key, null, VersionedCache.DELETED_VERSION);
    }

    /**
     * Background reload of a stale entry. It shares the coalescer key with blocking loads, so at most one load per
     * key runs on this instance, and takes the same lock, so other instances re-read the refreshed entry instead
     * of loading again. A failure only logs: the stale entry stays until the next attempt or its expiry.
     */
    private <T> void revalidate(VersionedKey key, Class<T> type, Supplier<VersionedValue<T>> loader) {
        try {
            requestCoalescer.submit(key, () -> loadUnderLock(key, type, loader))
                    .whenComplete((_, error) -> {
                        if (error != null) {
                            log.warn("Background revalidation of {} failed, serving stale until next try: {}",
                                    key, error.getMessage());
                        }
                    });
        } catch (RuntimeException ex) {
            log.warn("Could not start background revalidation of {}: {}", key, ex.getMessage());
        }
    }

    private <T> T loadUnderLock(VersionedKey key, Class<T> type, Supplier<VersionedValue<T>> loader) {
        String lockKey = key.qualified();
        Lock lock = lockRegistry.obtain(lockKey);
        boolean locked = tryLock(lock, lockKey);
        try {
            // Whether we got the lock or timed out waiting, another instance may have refreshed the cache meanwhile.
            // Only a fresh entry counts: a stale one is exactly what this load is replacing.
            VersionedCache.Entry<T> cached = read(key, type);
            if (cached != null && cached.fresh()) {
                return cached.value();
            }

            return storeLoaded(key, type, loader.get());
        } finally {
            if (locked) {
                unlock(lock, lockKey);
            }
        }
    }

    /** Stores what the loader returned; if a newer version won the race, returns that one instead. */
    private <T> T storeLoaded(VersionedKey key, Class<T> type, VersionedValue<T> loaded) {
        T value = loaded == null ? null : loaded.value();
        long version = loaded == null ? VersionedCache.NOT_FOUND_VERSION : loaded.version();

        if (write(key, value, version)) {
            return value;
        }
        // an equal (now renewed) or newer version is stored: prefer it over what we loaded
        VersionedCache.Entry<T> stored = read(key, type);
        return stored != null ? stored.value() : value;
    }

    private boolean tryLock(Lock lock, String lockKey) {
        try {
            boolean locked = lock.tryLock(lockProperties.getWaitTime().toMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                log.warn("Lock {} not acquired within {}, loading without it", lockKey, lockProperties.getWaitTime());
            }
            return locked;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for lock " + lockKey, ex);
        } catch (RuntimeException ex) {
            log.warn("Lock {} unavailable ({}), loading without it", lockKey, ex.getMessage());
            return false;
        }
    }

    private void unlock(Lock lock, String lockKey) {
        try {
            lock.unlock();
        } catch (RuntimeException ex) {
            // e.g. the lease expired before the load finished, or Redis went away
            log.warn("Failed to release lock {}: {}", lockKey, ex.getMessage());
        }
    }

    private <T> VersionedCache.Entry<T> read(VersionedKey key, Class<T> type) {
        try {
            return cache.get(key, type);
        } catch (RuntimeException ex) {
            log.warn("Cache read failed for {} ({}), treating as miss", key, ex.getMessage());
            return null;
        }
    }

    private boolean write(VersionedKey key, Object value, long version) {
        try {
            boolean written = cache.putIfNewer(key, value, version);
            if (!written) {
                log.debug("Skipped caching {} at version {}: an equal or newer version is stored", key, version);
            }
            return written;
        } catch (RuntimeException ex) {
            log.warn("Cache write failed for {}: {}", key, ex.getMessage());
            return false;
        }
    }
}
