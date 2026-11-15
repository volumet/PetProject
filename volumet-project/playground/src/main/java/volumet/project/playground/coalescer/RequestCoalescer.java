package volumet.project.playground.coalescer;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import volumet.project.exceptions.integration.CoalescerTimeoutException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Topic 1 — single-flight request coalescer. YOUR IMPLEMENTATION GOES HERE.
 * <p>
 * Concurrent calls with the same key share ONE execution of the loader; everyone gets the same result.
 * Once that execution finishes (success or failure), the key is released and the next call loads again
 * (this is not a cache).
 * <p>
 * The spec is {@code RequestCoalescerTest}. Read it, then make it green. You may add fields and private
 * methods freely; keep the constructor and public signatures, the tests use them.
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RequestCoalescer {
    ConcurrentMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    /** Runs the loaders. Never run a loader on the caller's thread. */
    Executor executor;

    /** Used by {@link #execute(String, Supplier)}. */
    Duration defaultTimeout;

    /**
     * Non-blocking: starts the load for {@code key}, or joins the one already in flight, and returns at once.
     * <p>
     * Each caller must be able to cancel / time out its own future without affecting the other callers
     * or the shared load.
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> submit(String key, Supplier<? extends T> loader) {
        CompletableFuture<Object> leader = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(key, leader);

        if (existing != null) {
            return (CompletableFuture<T>) existing.copy();
        }

        try {
            CompletableFuture.supplyAsync(loader, executor).whenComplete((rs, ex) -> {
                try {
                    if (ex != null) {
                        leader.completeExceptionally(ex);
                    }
                    else {
                        leader.complete(rs);
                    }
                } finally {
                    inFlight.remove(key, leader);
                }
            });
        } catch (Throwable ex) {
            try {
                leader.completeExceptionally(ex);
            } finally {
                inFlight.remove(key, leader);
            }
        }

        return (CompletableFuture<T>) leader.copy();
    }

    /** Blocking variant with {@link #defaultTimeout}. */
    public <T> T execute(String key, Supplier<? extends T> loader) {
        return execute(key, loader, defaultTimeout);
    }

    /**
     * Blocking variant for request threads.
     * <ul>
     *   <li>returns the loader's result (may be {@code null});</li>
     *   <li>rethrows the loader's {@link RuntimeException} / {@link Error} unchanged (same instance, not wrapped),
     *       so a central exception handler maps it as if the loader had been called directly;</li>
     *   <li>throws {@link CoalescerTimeoutException} after {@code timeout}, without cancelling the shared load;</li>
     *   <li>when the waiting thread is interrupted, keeps its interrupt flag set and throws.</li>
     * </ul>
     */
    public <T> T execute(String key, Supplier<? extends T> loader, Duration timeout) {
        CompletableFuture<T> request = submit(key, loader);
        T result;
        try {
            result = request.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        }
        catch (TimeoutException ex) {
            request.cancel(true);
            throw new CoalescerTimeoutException(key, timeout);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for coalesced request " + key);
        }
        catch (ExecutionException ex) {
            Throwable error = unwrap(ex);
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (error instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException("Interrupted while waiting for coalesced request " + key);
        }

        return result;
    }

    private Throwable unwrap(Throwable ex) {
        Throwable current = ex;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }

}
