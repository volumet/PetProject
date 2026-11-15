package volumet.project.playground.coalescer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
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
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Specification for {@link CoalescerTaskExecutor}. Make every test green without changing this file.
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class CoalescerTaskExecutorTest {

    private static final String PREFIX = "request-coalescer-";
    private static final Duration LONG = Duration.ofSeconds(2);

    private final CountDownLatch release = new CountDownLatch(1);
    private final List<CoalescerTaskExecutor> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        release.countDown();
        created.forEach(CoalescerTaskExecutor::close);
        MDC.clear();
    }

    private CoalescerTaskExecutor executor(int limit, Duration acquireTimeout, Duration awaitTermination) {
        CoalescerTaskExecutor executor =
                new CoalescerTaskExecutor(new CoalescerExecutorSettings(PREFIX, limit, acquireTimeout, awaitTermination));
        created.add(executor);
        return executor;
    }

    private CoalescerTaskExecutor executor(int limit) {
        return executor(limit, LONG, LONG);
    }

    /** Runs {@code body} on the executor and returns its result. */
    private static <T> T runOn(CoalescerTaskExecutor executor, Supplier<T> body) {
        return CompletableFuture.supplyAsync(body, executor).orTimeout(1, TimeUnit.SECONDS).join();
    }

    // ---------------------------------------------------------------- settings

    @Test
    @DisplayName("invalid settings are refused when the executor is built")
    void invalidSettingsAreRefused() {
        // Built lazily: validation may live in the record's compact constructor OR in the executor's
        // constructor, so the settings must be created inside catchThrowable too.
        Map<String, Supplier<CoalescerExecutorSettings>> invalid = new LinkedHashMap<>();
        invalid.put("null settings", () -> null);
        invalid.put("limit 0", () -> new CoalescerExecutorSettings(PREFIX, 0, LONG, LONG));
        invalid.put("limit -2", () -> new CoalescerExecutorSettings(PREFIX, -2, LONG, LONG));
        invalid.put("blank prefix", () -> new CoalescerExecutorSettings(" ", 1, LONG, LONG));
        invalid.put("null prefix", () -> new CoalescerExecutorSettings(null, 1, LONG, LONG));
        invalid.put("negative acquireTimeout", () -> new CoalescerExecutorSettings(PREFIX, 1, Duration.ofMillis(-1), LONG));
        invalid.put("null acquireTimeout", () -> new CoalescerExecutorSettings(PREFIX, 1, null, LONG));
        invalid.put("negative awaitTermination", () -> new CoalescerExecutorSettings(PREFIX, 1, LONG, Duration.ofMillis(-1)));
        invalid.put("null awaitTermination", () -> new CoalescerExecutorSettings(PREFIX, 1, LONG, null));

        invalid.forEach((name, settings) -> {
            Throwable thrown = catchThrowable(() -> new CoalescerTaskExecutor(settings.get()));
            assertThat(thrown).as(name)
                    .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
        });
    }

    @Test
    @DisplayName("a null task is refused")
    void nullTaskIsRefused() {
        assertThatThrownBy(() -> executor(1).execute(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------- threads

    @Test
    @DisplayName("each task runs on its own new virtual thread named <prefix><n>")
    void runsOnNamedVirtualThreads() {
        CoalescerTaskExecutor executor = executor(CoalescerExecutorSettings.UNLIMITED);
        Set<String> names = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < 3; i++) {
            Thread worker = runOn(executor, Thread::currentThread);
            assertThat(worker.isVirtual()).isTrue();
            assertThat(worker.getName()).matches(PREFIX + "\\d+");
            names.add(worker.getName());
        }
        assertThat(names).as("thread names are unique").hasSize(3);
    }

    @Test
    @DisplayName("execute does not run the task on the submitting thread")
    void doesNotRunOnCaller() {
        Thread caller = Thread.currentThread();
        assertThat(runOn(executor(1), Thread::currentThread)).isNotSameAs(caller);
    }

    // ---------------------------------------------------------------- concurrency limit

    @Test
    @DisplayName("never runs more than concurrencyLimit tasks at once")
    void respectsConcurrencyLimit() throws Exception {
        int limit = 4;
        int tasks = 40;
        CoalescerTaskExecutor executor = executor(limit);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(tasks);
        CyclicBarrier start = new CyclicBarrier(tasks);

        try (ExecutorService submitters = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                submitters.submit(() -> {
                    start.await();
                    executor.execute(() -> {
                        maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                        sleepQuietly(5);
                        running.decrementAndGet();
                        done.countDown();
                    });
                    return null;
                });
            }
        }

        assertThat(done.await(3, TimeUnit.SECONDS)).as("all tasks ran").isTrue();
        assertThat(maxRunning.get()).isBetween(1, limit);
    }

    @Test
    @DisplayName("when full, execute waits acquireTimeout and then rejects; a finished task frees its slot")
    void rejectsWhenFullThenRecovers() throws Exception {
        CoalescerTaskExecutor executor = executor(1, Duration.ofMillis(100), LONG);
        CountDownLatch firstStarted = new CountDownLatch(1);
        executor.execute(() -> {
            firstStarted.countDown();
            awaitQuietly(release);
        });
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

        long startNanos = System.nanoTime();
        assertThatThrownBy(() -> executor.execute(() -> { }))
                .isInstanceOf(RejectedExecutionException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - startNanos))
                .as("waited for a slot before rejecting").isGreaterThanOrEqualTo(Duration.ofMillis(90));

        release.countDown();
        assertThat(runOn(executor, () -> "next")).isEqualTo("next");
    }

    @Test
    @DisplayName("when full, a submitter blocks until a slot frees up (within acquireTimeout)")
    void blocksUntilSlotFrees() throws Exception {
        CoalescerTaskExecutor executor = executor(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        executor.execute(() -> {
            firstStarted.countDown();
            awaitQuietly(release);
        });
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

        CountDownLatch secondRan = new CountDownLatch(1);
        Thread submitter = Thread.ofVirtual().start(() -> executor.execute(secondRan::countDown));

        assertThat(secondRan.await(100, TimeUnit.MILLISECONDS)).as("second task waits for the slot").isFalse();
        release.countDown();
        assertThat(secondRan.await(1, TimeUnit.SECONDS)).as("second task runs once the slot frees").isTrue();
        submitter.join();
    }

    @Test
    @DisplayName("a task that throws still frees its slot")
    void failingTaskFreesSlot() {
        CoalescerTaskExecutor executor = executor(1, Duration.ofMillis(500), LONG);

        for (int i = 0; i < 5; i++) {
            Throwable thrown = catchThrowable(() -> runOn(executor, () -> { throw new IllegalStateException("boom"); }));
            assertThat(thrown).hasRootCauseInstanceOf(IllegalStateException.class);
        }
        assertThat(runOn(executor, () -> "still works")).isEqualTo("still works");
    }

    @Test
    @DisplayName("a submitter interrupted while waiting for a slot is rejected and keeps its interrupt flag")
    void interruptedSubmitter() throws Exception {
        CoalescerTaskExecutor executor = executor(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        executor.execute(() -> {
            firstStarted.countDown();
            awaitQuietly(release);
        });
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean stillInterrupted = new AtomicBoolean();
        Thread submitter = Thread.ofVirtual().unstarted(() -> {
            Thread.currentThread().interrupt();
            thrown.set(catchThrowable(() -> executor.execute(() -> { })));
            stillInterrupted.set(Thread.currentThread().isInterrupted());
        });
        submitter.start();
        submitter.join();

        assertThat(thrown.get()).isInstanceOf(RejectedExecutionException.class);
        assertThat(stillInterrupted).isTrue();
    }

    // ---------------------------------------------------------------- MDC

    @Test
    @DisplayName("the task sees the submitter's MDC")
    void propagatesMdc() {
        MDC.put("correlationId", "abc-123");
        assertThat(runOn(executor(1), () -> MDC.get("correlationId"))).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("the submitter's MDC is left intact, and a later task does not see an earlier task's MDC")
    void mdcIsIsolated() {
        CoalescerTaskExecutor executor = executor(1);
        MDC.put("correlationId", "abc-123");

        runOn(executor, () -> {
            MDC.put("leaked", "yes");
            MDC.remove("correlationId");
            return null;
        });
        assertThat(MDC.get("correlationId")).as("submitter keeps its correlation id").isEqualTo("abc-123");
        assertThat(MDC.get("leaked")).isNull();

        MDC.clear();
        assertThat(runOn(executor, () -> MDC.getCopyOfContextMap()))
                .as("a submitter without MDC gives the task an empty MDC")
                .satisfiesAnyOf(map -> assertThat(map).isNull(), map -> assertThat(map).isEmpty());
    }

    // ---------------------------------------------------------------- shutdown

    @Test
    @DisplayName("close() rejects new tasks and is idempotent")
    void closeRejectsNewTasks() {
        CoalescerTaskExecutor executor = executor(1);
        executor.close();
        executor.close();

        assertThatThrownBy(() -> executor.execute(() -> { })).isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    @DisplayName("close() waits for running tasks that finish within awaitTermination")
    void closeWaitsForRunningTasks() throws Exception {
        CoalescerTaskExecutor executor = executor(2, LONG, LONG);
        AtomicBoolean finished = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            sleepQuietly(150);
            finished.set(true);
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        executor.close();
        assertThat(finished).as("close() returned only after the running task finished").isTrue();
    }

    @Test
    @DisplayName("close() gives up after awaitTermination and interrupts tasks still running")
    void closeInterruptsStragglers() throws Exception {
        CoalescerTaskExecutor executor = executor(1, LONG, Duration.ofMillis(150));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();       // would block forever
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        long startNanos = System.nanoTime();
        executor.close();
        Duration took = Duration.ofNanos(System.nanoTime() - startNanos);

        assertThat(took).isBetween(Duration.ofMillis(140), Duration.ofSeconds(1));
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).as("straggler was interrupted").isTrue();
    }

    // ---------------------------------------------------------------- with the coalescer

    @Test
    @DisplayName("drives RequestCoalescer: 64 concurrent callers, one load, even with a limit of 1")
    void worksUnderRequestCoalescer() throws Exception {
        CoalescerTaskExecutor executor = executor(1);
        RequestCoalescer coalescer = new RequestCoalescer(executor, LONG);
        AtomicInteger loads = new AtomicInteger();
        MDC.put("correlationId", "leader");

        CompletableFuture<String> leader = coalescer.submit("room:1", () -> {
            loads.incrementAndGet();
            awaitQuietly(release);
            return "room-1@" + MDC.get("correlationId");
        });

        // all followers join while the leader's load holds the only slot; they must not need a slot themselves
        List<Future<CompletableFuture<String>>> followers = new ArrayList<>();
        try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 64; i++) {
                followers.add(callers.submit(() -> coalescer.<String>submit("room:1", () -> {
                    loads.incrementAndGet();
                    return "second load!";
                })));
            }
        }
        release.countDown();

        assertThat(leader.get(1, TimeUnit.SECONDS)).isEqualTo("room-1@leader");
        for (Future<CompletableFuture<String>> follower : followers) {
            assertThat(follower.get().get(1, TimeUnit.SECONDS)).isEqualTo("room-1@leader");
        }
        assertThat(loads).hasValue(1);
    }

    // ---------------------------------------------------------------- helpers

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
