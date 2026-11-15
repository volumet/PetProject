package volumet.project.components;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import volumet.project.configs.RequestCoalescerExecutorConfig;
import volumet.project.configs.properties.RequestCoalescerExecutorProperties;
import volumet.project.exceptions.integration.CoalescedRequestTimeoutException;
import volumet.project.keys.KeySpace;
import volumet.project.keys.VersionedKey;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestCoalescerTest {
    private static final KeySpace ROOMS = KeySpace.of("rooms", 1);
    private static final Duration WAIT = Duration.ofSeconds(5);

    private SimpleAsyncTaskExecutor executor;
    private RequestCoalescer coalescer;
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger loads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        RequestCoalescerExecutorProperties properties = new RequestCoalescerExecutorProperties();
        executor = new RequestCoalescerExecutorConfig().requestCoalescerTaskExecutor(properties);
        coalescer = new RequestCoalescer(executor, properties);
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        executor.close();
        MDC.clear();
    }

    /** Loader that blocks until {@link #release} is counted down, so concurrent callers overlap deterministically. */
    private String blockingLoad(String value) {
        loads.incrementAndGet();
        try {
            if (!release.await(WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("test loader was never released");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
        return value;
    }

    @Test
    void concurrentCallersWithSameKeyShareOneLoad() throws Exception {
        VersionedKey key = ROOMS.key(1);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(coalescer.submit(key, () -> blockingLoad("room-1")));
        }

        release.countDown();

        for (CompletableFuture<String> future : futures) {
            assertThat(future.get(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo("room-1");
        }
        assertThat(loads).hasValue(1);
    }

    @Test
    void differentKeysAndVersionsLoadSeparately() throws Exception {
        CompletableFuture<String> roomOne = coalescer.submit(ROOMS.key(1), () -> blockingLoad("1"));
        CompletableFuture<String> roomTwo = coalescer.submit(ROOMS.key(2), () -> blockingLoad("2"));
        CompletableFuture<String> roomOneV2 = coalescer.submit(KeySpace.of("rooms", 2).key(1), () -> blockingLoad("1-v2"));

        release.countDown();

        assertThat(roomOne.get(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo("1");
        assertThat(roomTwo.get(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo("2");
        assertThat(roomOneV2.get(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo("1-v2");
        assertThat(loads).hasValue(3);
    }

    @Test
    void keyIsReleasedAfterCompletionSoTheNextCallLoadsAgain() {
        release.countDown();
        VersionedKey key = ROOMS.key(1);

        coalescer.execute(key, () -> blockingLoad("first"), WAIT);
        String second = coalescer.execute(key, () -> blockingLoad("second"), WAIT);

        assertThat(second).isEqualTo("second");
        assertThat(loads).hasValue(2);
    }

    /** Regression: followers used to receive the shared future, so one timing out cancelled the load for all. */
    @Test
    void followerTimeoutDoesNotCancelTheSharedLoad() throws Exception {
        VersionedKey key = ROOMS.key(99);
        CompletableFuture<String> leader = coalescer.submit(key, () -> blockingLoad("ok"));

        assertThatThrownBy(() -> coalescer.execute(key, () -> "unused", Duration.ofMillis(50)))
                .isInstanceOfSatisfying(CoalescedRequestTimeoutException.class, ex -> {
                    assertThat(ex.getKey()).isEqualTo(key);
                    assertThat(ex.getTimeout()).isEqualTo(Duration.ofMillis(50));
                });

        release.countDown();
        assertThat(leader.get(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo("ok");
        assertThat(loads).hasValue(1);
    }

    @Test
    void cancellingOneCallersFutureDoesNotAffectOtherCallers() throws Exception {
        VersionedKey key = ROOMS.key(5);
        CompletableFuture<String> first = coalescer.submit(key, () -> blockingLoad("value"));
        CompletableFuture<String> second = coalescer.submit(key, () -> blockingLoad("value"));

        first.cancel(true);
        release.countDown();

        assertThat(first).isCancelled();
        assertThat(second.get(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo("value");
    }

    @Test
    void loaderRuntimeExceptionIsRethrownUnchangedToEveryCaller() throws Exception {
        VersionedKey key = ROOMS.key(7);
        IllegalStateException failure = new IllegalStateException("db down");
        CountDownLatch loadStarted = new CountDownLatch(1);

        // leader blocks in execute(); its load signals once it is in flight
        CompletableFuture<Object> leader = CompletableFuture.supplyAsync(() -> coalescer.execute(key, () -> {
            loadStarted.countDown();
            blockingLoad("never");
            throw failure;
        }, WAIT));
        assertThat(loadStarted.await(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        // the load is still in flight, so this caller is guaranteed to join it
        CompletableFuture<String> follower = coalescer.submit(key, () -> "unused");

        release.countDown();

        // the cause is the loader's own exception, not a wrapper: execute() rethrew it unchanged
        assertThat(leader).failsWithin(WAIT).withThrowableThat().havingCause().isSameAs(failure);
        assertThat(follower).failsWithin(WAIT).withThrowableThat().havingRootCause().isSameAs(failure);
        assertThat(loads).hasValue(1);
    }

    @Test
    void timeoutThrowsCoalescedRequestTimeoutException() {
        VersionedKey key = ROOMS.key(8);

        assertThatThrownBy(() -> coalescer.execute(key, () -> blockingLoad("slow"), Duration.ofMillis(50)))
                .isInstanceOf(CoalescedRequestTimeoutException.class)
                .hasMessageContaining("rooms:v1:8");
    }

    @Test
    void defaultTimeoutComesFromProperties() {
        RequestCoalescerExecutorProperties properties = new RequestCoalescerExecutorProperties();
        properties.setDefaultTimeout(Duration.ofMillis(50));
        RequestCoalescer shortTimeout = new RequestCoalescer(executor, properties);

        assertThatThrownBy(() -> shortTimeout.execute(ROOMS.key(9), () -> blockingLoad("slow")))
                .isInstanceOf(CoalescedRequestTimeoutException.class);
    }

    @Test
    void loadRunsOnVirtualThreadWithTheCallersCorrelationId() {
        MDC.put("correlationId", "abc-123");

        String observed = coalescer.execute(ROOMS.key(10),
                () -> Thread.currentThread().isVirtual() + "/" + MDC.get("correlationId"), WAIT);

        assertThat(observed).isEqualTo("true/abc-123");
    }

    @Test
    void callersOwnCorrelationIdIsLeftIntactAfterTheLoad() {
        MDC.put("correlationId", "abc-123");

        coalescer.execute(ROOMS.key(11), () -> "value", WAIT);

        // the request keeps logging with its correlation id after a coalesced cache miss
        assertThat(MDC.get("correlationId")).isEqualTo("abc-123");
    }
}
