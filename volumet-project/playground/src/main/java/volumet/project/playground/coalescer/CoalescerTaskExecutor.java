package volumet.project.playground.coalescer;

import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Topic 1b — the executor that runs {@link RequestCoalescer} loads. YOUR IMPLEMENTATION GOES HERE.
 * <p>
 * Plain Java (no Spring {@code SimpleAsyncTaskExecutor}). Requirements, all checked by
 * {@code CoalescerTaskExecutorTest}:
 * <ul>
 *   <li><b>one virtual thread per task</b>, no pool, no queue; named {@code <prefix><n>} (unique, counting from 1);</li>
 *   <li><b>concurrency limit</b>: at most {@code concurrencyLimit} tasks run at once ({@code -1} = unlimited).
 *       When full, {@link #execute} blocks the submitter up to {@code acquireTimeout}, then throws
 *       {@link RejectedExecutionException}. A slot is freed when a task ends, <i>also when it throws</i>;</li>
 *   <li>a submitter interrupted while waiting for a slot gets {@link RejectedExecutionException} and keeps its
 *       interrupt flag;</li>
 *   <li><b>MDC propagation</b>: the task sees the submitter's MDC (correlation id). The submitter's own MDC is
 *       left untouched, and the worker's MDC is cleared when the task ends;</li>
 *   <li><b>graceful shutdown</b>: {@link #close()} stops accepting tasks ({@link RejectedExecutionException}),
 *       waits up to {@code awaitTermination} for running tasks, then interrupts the ones still running.
 *       Calling it twice is harmless.</li>
 * </ul>
 * You may add fields, private methods and helper classes freely; keep the constructor and public signatures.
 */
public class CoalescerTaskExecutor implements Executor, AutoCloseable {
    private final ExecutorService executorService;
    private final Semaphore semaphore;
    private final CoalescerExecutorSettings settings;

    public CoalescerTaskExecutor(CoalescerExecutorSettings settings) {
        ThreadFactory factory = Thread.ofVirtual().name(settings.threadNamePrefix(), 1).factory();
        this.executorService = Executors.newThreadPerTaskExecutor(factory);
        this.settings = settings;

        if (settings.concurrencyLimit() == CoalescerExecutorSettings.UNLIMITED) {
            semaphore = null;
        }
        else {
            this.semaphore = new Semaphore(settings.concurrencyLimit());
        }
    }

    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task);
        Map<String, String> threadLocalContextMap = MDC.getCopyOfContextMap();

        if (executorService.isShutdown()) {
            throw new RejectedExecutionException("Executor is shutdown");
        }
        acquirePermit();
        try {
            executorService.execute(() -> {
                try {
                    mdcDecorator(task, threadLocalContextMap).run();
                } finally {
                    releasePermit();
                }
            });
        } catch (RuntimeException ex) {
            releasePermit();
            throw ex;
        }
    }

    private void acquirePermit() {
        boolean acquired;
        if (semaphore == null) {
            return;
        }
        try {
            acquired = semaphore.tryAcquire(settings.acquireTimeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Interrupted while acquiring throttle", e);
        }

        if (!acquired) {
            throw new RejectedExecutionException("Unable to acquire the semaphore");
        }
    }

    private void releasePermit() {
        if (semaphore == null) {
            return;
        }

        semaphore.release();
    }

    @Override
    public void close() {
        try {
            executorService.shutdown();
            boolean awaitedTermination = executorService.awaitTermination(settings.awaitTermination().toNanos(), TimeUnit.NANOSECONDS);
            if (!awaitedTermination) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }

    }

    private Runnable mdcDecorator(Runnable task, Map<String, String> threadLocalContextMap) {
        return () -> {
            try {
                if (threadLocalContextMap != null) {
                    MDC.setContextMap(threadLocalContextMap);
                }

                task.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
