package volumet.project.playground.coalescer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import volumet.project.exceptions.integration.CoalescerTimeoutException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Specification for the topic-1 {@link RequestCoalescer}. Make every test green without changing this file.
 * <p>
 * Loaders block on a latch so the tests decide exactly when a load finishes; nothing here relies on sleeps.
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class RequestCoalescerTest {

    private static final Duration WAIT = Duration.ofSeconds(2);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final RequestCoalescer coalescer = new RequestCoalescer(executor, WAIT);

    private final AtomicInteger loads = new AtomicInteger();
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void shutdown() {
        release.countDown();
        executor.shutdownNow();
    }

    /** A loader that counts its runs and blocks until {@link #release} opens. */
    private <T> Supplier<T> blockingLoader(T value) {
        return () -> {
            loads.incrementAndGet();
            awaitQuietly(release);
            return value;
        };
    }

    // ---------------------------------------------------------------- coalescing

    @Test
    @DisplayName("concurrent calls with the same key run the loader once and share its result")
    void concurrentCallsShareOneLoad() throws Exception {
        int callers = 64;
        CyclicBarrier start = new CyclicBarrier(callers);
        Supplier<String> loader = blockingLoader("room-1");

        List<Future<CompletableFuture<String>>> submitted = new ArrayList<>();
        try (ExecutorService callerThreads = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < callers; i++) {
                submitted.add(callerThreads.submit(() -> {
                    start.await();                       // all callers race into submit at once
                    return coalescer.submit("room:1", loader);
                }));
            }
        }

        release.countDown();
        for (Future<CompletableFuture<String>> f : submitted) {
            assertThat(f.get().get(1, TimeUnit.SECONDS)).isEqualTo("room-1");
        }
        assertThat(loads).hasValue(1);
    }

    @Test
    @DisplayName("submit returns immediately and runs the loader on the executor, not the caller thread")
    void submitDoesNotBlockTheCaller() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> loaderThread = new AtomicReference<>();

        CompletableFuture<String> future = coalescer.submit("k", () -> {
            loaderThread.set(Thread.currentThread());
            awaitQuietly(release);
            return "v";
        });

        assertThat(future).isNotDone();
        release.countDown();
        assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("v");
        assertThat(loaderThread.get()).isNotNull().isNotSameAs(caller);
    }

    @Test
    @DisplayName("different keys load independently: one slow key does not hold up another")
    void differentKeysAreIndependent() throws Exception {
        CompletableFuture<String> slow = coalescer.submit("a", blockingLoader("A"));
        CompletableFuture<String> fast = coalescer.submit("b", () -> "B");

        assertThat(fast.get(1, TimeUnit.SECONDS)).isEqualTo("B");
        assertThat(slow).isNotDone();

        release.countDown();
        assertThat(slow.get(1, TimeUnit.SECONDS)).isEqualTo("A");
    }

    @Test
    @DisplayName("it is not a cache: once a load completes, the next call loads again")
    void keyIsReleasedAfterSuccess() {
        Supplier<Integer> loader = loads::incrementAndGet;

        assertThat(coalescer.execute("k", loader)).isEqualTo(1);
        assertThat(coalescer.execute("k", loader)).isEqualTo(2);
    }

    @Test
    @DisplayName("a null result is a valid result")
    void nullResult() {
        assertThat(coalescer.<String>execute("k", () -> null)).isNull();
        assertThat(coalescer.execute("k", () -> "next")).isEqualTo("next");
    }

    // ---------------------------------------------------------------- failures

    @Test
    @DisplayName("a failing load fails every waiter with the same exception, then the key is released")
    void failureIsSharedAndReleasesKey() throws Exception {
        IllegalStateException boom = new IllegalStateException("boom");
        Supplier<String> failing = () -> {
            loads.incrementAndGet();
            awaitQuietly(release);
            throw boom;
        };

        CompletableFuture<String> first = coalescer.submit("k", failing);
        CompletableFuture<String> second = coalescer.submit("k", failing);
        release.countDown();

        for (CompletableFuture<String> f : List.of(first, second)) {
            assertThatThrownBy(() -> f.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .rootCause().isSameAs(boom);
        }
        assertThat(loads).hasValue(1);

        // a failure is not remembered: the next call retries
        assertThat(coalescer.execute("k", () -> "recovered")).isEqualTo("recovered");
    }

    @Test
    @DisplayName("execute rethrows the loader's RuntimeException unchanged (not wrapped)")
    void executeRethrowsRuntimeExceptionUnwrapped() {
        IllegalArgumentException boom = new IllegalArgumentException("not found");

        assertThatThrownBy(() -> coalescer.execute("k", () -> { throw boom; }))
                .isSameAs(boom);
    }

    @Test
    @DisplayName("execute rethrows the loader's Error unchanged (not wrapped)")
    void executeRethrowsErrorUnwrapped() {
        AssertionError boom = new AssertionError("fatal");

        assertThatThrownBy(() -> coalescer.execute("k", () -> { throw boom; }))
                .isSameAs(boom);
    }

    @Test
    @DisplayName("if the executor rejects the load, the caller sees the rejection and the key is not left stuck")
    void rejectedExecutionReleasesKey() {
        AtomicBoolean rejectNext = new AtomicBoolean(true);
        Executor flaky = task -> {
            if (rejectNext.getAndSet(false)) {
                throw new RejectedExecutionException("executor saturated");
            }
            executor.execute(task);
        };
        RequestCoalescer flakyCoalescer = new RequestCoalescer(flaky, WAIT);

        assertThatThrownBy(() -> flakyCoalescer.execute("k", () -> "never"))
                .isInstanceOf(RejectedExecutionException.class);

        assertThat(flakyCoalescer.execute("k", () -> "ok")).isEqualTo("ok");
    }

    // ---------------------------------------------------------------- timeouts, cancellation, interrupts

    @Test
    @DisplayName("a caller that times out gets CoalescerTimeoutException; the shared load keeps going for others")
    void timeoutDoesNotCancelSharedLoad() throws Exception {
        Supplier<String> loader = blockingLoader("v");
        CompletableFuture<String> patient = coalescer.submit("k", loader);

        assertThatThrownBy(() -> coalescer.execute("k", loader, Duration.ofMillis(100)))
                .isInstanceOf(CoalescerTimeoutException.class);

        release.countDown();
        assertThat(patient.get(1, TimeUnit.SECONDS)).isEqualTo("v");
        assertThat(loads).hasValue(1);
    }

    @Test
    @DisplayName("execute(key, loader) uses the default timeout from the constructor")
    void defaultTimeoutIsUsed() {
        RequestCoalescer impatient = new RequestCoalescer(executor, Duration.ofMillis(100));

        assertThatThrownBy(() -> impatient.execute("k", blockingLoader("v")))
                .isInstanceOf(CoalescerTimeoutException.class);
    }

    @Test
    @DisplayName("cancelling one caller's future (even the first caller's) does not affect the others")
    void cancellingOneCallerIsIsolated() throws Exception {
        Supplier<String> loader = blockingLoader("v");
        CompletableFuture<String> leader = coalescer.submit("k", loader);
        CompletableFuture<String> follower = coalescer.submit("k", loader);
        CompletableFuture<String> another = coalescer.submit("k", loader);

        leader.cancel(true);
        follower.cancel(true);
        release.countDown();

        assertThat(another.get(1, TimeUnit.SECONDS)).isEqualTo("v");
        assertThat(loads).hasValue(1);
    }

    @Test
    @DisplayName("an interrupted waiter keeps its interrupt flag, and the shared load is not cancelled")
    void interruptIsPreserved() throws Exception {
        Supplier<String> loader = blockingLoader("v");
        CompletableFuture<String> other = coalescer.submit("k", loader);

        AtomicBoolean threw = new AtomicBoolean();
        AtomicBoolean stillInterrupted = new AtomicBoolean();
        Thread waiter = Thread.ofVirtual().unstarted(() -> {
            Thread.currentThread().interrupt();
            try {
                coalescer.execute("k", loader);
            } catch (RuntimeException expected) {
                threw.set(true);
            }
            stillInterrupted.set(Thread.currentThread().isInterrupted());
        });
        waiter.start();
        waiter.join();

        assertThat(threw).as("execute must throw when interrupted").isTrue();
        assertThat(stillInterrupted).as("interrupt flag must be restored").isTrue();

        release.countDown();
        assertThat(other.get(1, TimeUnit.SECONDS)).isEqualTo("v");
    }

    // ---------------------------------------------------------------- helpers

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
