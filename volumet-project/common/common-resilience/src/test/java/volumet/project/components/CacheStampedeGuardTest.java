package volumet.project.components;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.integration.support.locks.DefaultLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;
import volumet.project.cache.CacheReadPolicy;
import volumet.project.cache.InMemoryVersionedCache;
import volumet.project.cache.VersionedCache;
import volumet.project.cache.VersionedValue;
import volumet.project.configs.RequestCoalescerExecutorConfig;
import volumet.project.configs.properties.DistributedLockProperties;
import volumet.project.configs.properties.RequestCoalescerExecutorProperties;
import volumet.project.keys.KeySpace;
import volumet.project.keys.VersionedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Two guards sharing one cache and one lock registry stand in for two app instances sharing Redis.
 */
class CacheStampedeGuardTest {
    private static final KeySpace ROOMS = KeySpace.of("rooms", 1);
    private static final KeySpace ROOMS_V2 = KeySpace.of("rooms", 2);

    /** A plain API DTO: it knows nothing about versions; the version travels next to it in VersionedValue. */
    record Room(String name) {
    }

    private SimpleAsyncTaskExecutor executor;
    private RequestCoalescerExecutorProperties coalescerProperties;
    private final InMemoryVersionedCache cache = new InMemoryVersionedCache();
    private final LockRegistry lockRegistry = new DefaultLockRegistry();
    private final AtomicInteger loads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        coalescerProperties = new RequestCoalescerExecutorProperties();
        executor = new RequestCoalescerExecutorConfig().requestCoalescerTaskExecutor(coalescerProperties);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    private CacheStampedeGuard guard() {
        return guard(cache, lockRegistry);
    }

    private CacheStampedeGuard guard(VersionedCache versionedCache, LockRegistry locks) {
        return new CacheStampedeGuard(versionedCache, locks, new RequestCoalescer(executor, coalescerProperties),
                new DistributedLockProperties());
    }

    /** Simulates a DB read of a room at a given @Version. */
    private VersionedValue<Room> slowLoad(String name, long version) {
        loads.incrementAndGet();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return VersionedValue.of(new Room(name), version);
    }

    // ---- stampede protection ----

    @Test
    void concurrentReadsAcrossInstancesHitTheLoaderOnce() throws Exception {
        CacheStampedeGuard instanceA = guard();
        CacheStampedeGuard instanceB = guard();
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Room>> results = new ArrayList<>();

        try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 200; i++) {
                CacheStampedeGuard instance = i % 2 == 0 ? instanceA : instanceB;
                results.add(callers.submit(() -> {
                    go.await();
                    return instance.get(ROOMS.key(1), Room.class, () -> slowLoad("A1", 0));
                }));
            }
            go.countDown();
            for (Future<Room> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(new Room("A1"));
            }
        }

        // later callers either share the in-flight load or find the cached value; none reload
        assertThat(loads).hasValue(1);
    }

    @Test
    void returnsThePlainValueAndKeepsTheVersionAsEntryMetadata() {
        Room value = guard().get(ROOMS.key(2), Room.class, () -> slowLoad("A2", 5));

        assertThat(value).isEqualTo(new Room("A2"));
        assertThat(cache.get(ROOMS.key(2), Room.class)).isEqualTo(new VersionedCache.Entry<>(5L, new Room("A2")));
    }

    @Test
    void cacheHitDoesNotCallTheLoader() {
        cache.putIfNewer(ROOMS.key(3), new Room("cached"), 4L);

        Room value = guard().get(ROOMS.key(3), Room.class, () -> slowLoad("loaded", 4));

        assertThat(value.name()).isEqualTo("cached");
        assertThat(loads).hasValue(0);
    }

    @Test
    void notFoundResultIsCachedSoTheLoaderRunsOnce() {
        CacheStampedeGuard guard = guard();

        assertThat(guard.get(ROOMS.key(404), Room.class, () -> { loads.incrementAndGet(); return null; })).isNull();
        assertThat(guard.get(ROOMS.key(404), Room.class, () -> { loads.incrementAndGet(); return null; })).isNull();

        assertThat(loads).hasValue(1);
        assertThat(cache.storedVersion(ROOMS.key(404))).isEqualTo(VersionedCache.NOT_FOUND_VERSION);
    }

    @Test
    void schemaVersionsAreIsolated() {
        CacheStampedeGuard guard = guard();

        guard.get(ROOMS.key(7), Room.class, () -> slowLoad("v1-shape", 0));
        guard.get(ROOMS_V2.key(7), Room.class, () -> slowLoad("v2-shape", 0));
        Room again = guard.get(ROOMS.key(7), Room.class, () -> slowLoad("not-called", 0));

        assertThat(again.name()).isEqualTo("v1-shape");
        assertThat(loads).hasValue(2);
    }

    // ---- data version ordering ----

    /** The core rule: a load that read version 1 must not overwrite version 3 written meanwhile. */
    @Test
    void staleLoadDoesNotOverwriteNewerVersionWrittenMeanwhile() throws Exception {
        VersionedKey key = ROOMS.key(42);
        CacheStampedeGuard reader = guard();
        CacheStampedeGuard writer = guard();
        CountDownLatch readFromDb = new CountDownLatch(1);
        CountDownLatch updateCached = new CountDownLatch(1);

        // reader misses, reads version 1 from the DB, then is slow before caching it
        CompletableFuture<Room> staleRead = CompletableFuture.supplyAsync(() -> reader.get(key, Room.class, () -> {
            readFromDb.countDown();
            await(updateCached);
            return VersionedValue.of(new Room("old name"), 1L);
        }));
        await(readFromDb);

        // meanwhile an update commits version 3 and caches it
        assertThat(writer.put(key, new Room("new name"), 3L)).isTrue();
        updateCached.countDown();

        // the stale reader's write is rejected, and it returns the newer cached value instead of its own
        assertThat(staleRead.get(5, TimeUnit.SECONDS)).isEqualTo(new Room("new name"));
        assertThat(cache.storedVersion(key)).isEqualTo(3L);
        assertThat(reader.get(key, Room.class, () -> slowLoad("not-called", 0))).isEqualTo(new Room("new name"));
    }

    @Test
    void putReplacesOlderVersion() {
        VersionedKey key = ROOMS.key(1);
        CacheStampedeGuard guard = guard();

        assertThat(guard.put(key, new Room("v1"), 1L)).isTrue();
        assertThat(guard.put(key, new Room("v2"), 2L)).isTrue();

        assertThat(cache.get(key, Room.class)).isEqualTo(new VersionedCache.Entry<>(2L, new Room("v2")));
    }

    @Test
    void putRejectsOlderOrEqualVersion() {
        VersionedKey key = ROOMS.key(1);
        CacheStampedeGuard guard = guard();
        guard.put(key, new Room("v3"), 3L);

        assertThat(guard.put(key, new Room("v1"), 1L)).isFalse();
        // equal version: data kept (only the TTL is renewed)
        assertThat(guard.put(key, new Room("v3 again"), 3L)).isFalse();

        assertThat(cache.get(key, Room.class).value()).isEqualTo(new Room("v3"));
    }

    @Test
    void createdEntityReplacesCachedNotFound() {
        VersionedKey key = ROOMS.key(5);
        CacheStampedeGuard guard = guard();
        guard.get(key, Room.class, () -> null);

        // a new entity's @Version starts at 0, above the not-found marker
        assertThat(guard.put(key, new Room("created"), 0L)).isTrue();

        assertThat(guard.get(key, Room.class, () -> null)).isEqualTo(new Room("created"));
    }

    @Test
    void deletionMarkerCannotBeOverwrittenByAStaleLoad() {
        VersionedKey key = ROOMS.key(6);
        CacheStampedeGuard guard = guard();
        guard.put(key, new Room("before delete"), 2L);

        guard.markDeleted(key);
        // a load that read the row just before the delete tries to cache it
        boolean staleWrite = guard.put(key, new Room("before delete"), 2L);

        assertThat(staleWrite).isFalse();
        assertThat(guard.get(key, Room.class, () -> slowLoad("deleted row", 2))).isNull();
        assertThat(loads).hasValue(0);
    }

    @Test
    void putRequiresAValue() {
        assertThatNullPointerException().isThrownBy(() -> guard().put(ROOMS.key(9), null, 1L));
    }

    @Test
    void versionedValueRequiresAValue() {
        // "not found" is a null VersionedValue, never a VersionedValue holding null
        assertThatNullPointerException().isThrownBy(() -> VersionedValue.of(null, 1L));
    }

    // ---- read policies: stale entries ----

    @Test
    void freshOnlyTreatsAStaleEntryAsAMissAndWaitsForTheReload() {
        VersionedKey key = ROOMS.key(20);
        cache.putIfNewer(key, new Room("old"), 1L);
        cache.makeStale(key);

        Room value = guard().get(key, Room.class, CacheReadPolicy.FRESH_ONLY, () -> slowLoad("new", 2));

        assertThat(value).isEqualTo(new Room("new"));
        assertThat(cache.get(key, Room.class)).isEqualTo(new VersionedCache.Entry<>(2L, new Room("new")));
    }

    @Test
    void defaultPolicyIsFreshOnly() {
        VersionedKey key = ROOMS.key(21);
        cache.putIfNewer(key, new Room("old"), 1L);
        cache.makeStale(key);

        assertThat(guard().get(key, Room.class, () -> slowLoad("new", 2))).isEqualTo(new Room("new"));
    }

    @Test
    void staleWhileRevalidateReturnsTheStaleEntryAndRefreshesInTheBackground() {
        VersionedKey key = ROOMS.key(22);
        cache.putIfNewer(key, new Room("old"), 1L);
        cache.makeStale(key);
        CountDownLatch release = new CountDownLatch(1);

        Room value = guard().get(key, Room.class, CacheReadPolicy.STALE_WHILE_REVALIDATE, () -> {
            loads.incrementAndGet();
            await(release); // the reload is still running when get() has already returned
            return VersionedValue.of(new Room("new"), 2L);
        });

        assertThat(value).isEqualTo(new Room("old"));
        release.countDown();
        awaitUntil(() -> cache.storedVersion(key) == 2L);
        assertThat(cache.get(key, Room.class)).isEqualTo(new VersionedCache.Entry<>(2L, new Room("new")));
        assertThat(loads).hasValue(1);
    }

    @Test
    void concurrentStaleHitsAcrossInstancesTriggerOneReload() throws Exception {
        VersionedKey key = ROOMS.key(23);
        cache.putIfNewer(key, new Room("old"), 1L);
        cache.makeStale(key);
        CacheStampedeGuard instanceA = guard();
        CacheStampedeGuard instanceB = guard();
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Room>> results = new ArrayList<>();

        try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                CacheStampedeGuard instance = i % 2 == 0 ? instanceA : instanceB;
                results.add(callers.submit(() -> {
                    go.await();
                    return instance.get(key, Room.class, CacheReadPolicy.STALE_WHILE_REVALIDATE, () -> slowLoad("new", 2));
                }));
            }
            go.countDown();
            for (Future<Room> result : results) {
                // every caller is answered from the cache, old or (once reloaded) new, without waiting on the DB
                assertThat(result.get(10, TimeUnit.SECONDS).name()).isIn("old", "new");
            }
        }

        awaitUntil(() -> cache.storedVersion(key) == 2L);
        assertThat(loads).hasValue(1);
    }

    @Test
    void staleWhileRevalidateStillLoadsBlockingOnAMiss() {
        Room value = guard().get(ROOMS.key(24), Room.class, CacheReadPolicy.STALE_WHILE_REVALIDATE, () -> slowLoad("A24", 0));

        assertThat(value).isEqualTo(new Room("A24"));
        assertThat(loads).hasValue(1);
    }

    @Test
    void failedRevalidationKeepsServingTheStaleEntry() {
        VersionedKey key = ROOMS.key(25);
        cache.putIfNewer(key, new Room("old"), 1L);
        cache.makeStale(key);
        CacheStampedeGuard guard = guard();

        Room first = guard.get(key, Room.class, CacheReadPolicy.STALE_WHILE_REVALIDATE, () -> {
            loads.incrementAndGet();
            throw new IllegalStateException("db down");
        });
        awaitUntil(() -> loads.get() == 1);

        assertThat(first).isEqualTo(new Room("old"));
        assertThat(cache.get(key, Room.class)).isEqualTo(new VersionedCache.Entry<>(1L, new Room("old"), false));
    }

    @Test
    void reloadOfAnUnchangedVersionMakesTheEntryFreshAgain() {
        VersionedKey key = ROOMS.key(26);
        cache.putIfNewer(key, new Room("same"), 4L);
        cache.makeStale(key);
        CacheStampedeGuard guard = guard();

        guard.get(key, Room.class, CacheReadPolicy.FRESH_ONLY, () -> slowLoad("same", 4));
        Room again = guard.get(key, Room.class, CacheReadPolicy.FRESH_ONLY, () -> slowLoad("not-called", 4));

        assertThat(again).isEqualTo(new Room("same"));
        assertThat(cache.get(key, Room.class).fresh()).isTrue();
        assertThat(loads).hasValue(1);
    }

    // ---- degradation ----

    @Test
    void lockFailureStillLoadsAndCaches() {
        Room value = guard(cache, lockKey -> new FailingLock()).get(ROOMS.key(5), Room.class, () -> slowLoad("A5", 0));

        assertThat(value.name()).isEqualTo("A5");
        assertThat(cache.storedVersion(ROOMS.key(5))).isZero();
    }

    @Test
    void lockTimeoutStillLoads() {
        Room value = guard(cache, lockKey -> new NeverAvailableLock()).get(ROOMS.key(6), Room.class, () -> slowLoad("A6", 0));

        assertThat(value.name()).isEqualTo("A6");
    }

    @Test
    void cacheFailureIsTreatedAsMiss() {
        VersionedCache redisDown = new VersionedCache() {
            @Override
            public <T> Entry<T> get(VersionedKey key, Class<T> type) {
                throw new IllegalStateException("redis down");
            }

            @Override
            public boolean putIfNewer(VersionedKey key, Object value, long version) {
                throw new IllegalStateException("redis down");
            }

            @Override
            public void evict(VersionedKey key) {
                throw new IllegalStateException("redis down");
            }
        };

        Room value = guard(redisDown, lockRegistry).get(ROOMS.key(8), Room.class, () -> slowLoad("A8", 0));

        assertThat(value.name()).isEqualTo("A8");
        assertThat(loads).hasValue(1);
        assertThat(guard(redisDown, lockRegistry).put(ROOMS.key(8), new Room("A8"), 1L)).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("condition not met in time");
            }
            Thread.onSpinWait();
        }
    }

    /** Lock that behaves like Redis being unreachable. */
    private static class FailingLock extends NeverAvailableLock {
        @Override
        public boolean tryLock(long time, TimeUnit unit) {
            throw new IllegalStateException("redis down");
        }
    }

    /** Lock held by someone else for longer than the wait time. */
    private static class NeverAvailableLock implements Lock {
        @Override
        public boolean tryLock(long time, TimeUnit unit) {
            return false;
        }

        @Override
        public void lock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void lockInterruptibly() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryLock() {
            return false;
        }

        @Override
        public void unlock() {
            throw new IllegalStateException("not held");
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }
}
