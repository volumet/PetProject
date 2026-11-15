package volumet.project.components;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import volumet.project.configs.properties.RequestCoalescerExecutorProperties;
import volumet.project.exceptions.integration.CoalescedRequestTimeoutException;
import volumet.project.keys.VersionedKey;

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
 * Single-flight request coalescer: concurrent calls with the same key share one execution of the loader.
 * The first caller (leader) starts the load on the request-coalescer executor; callers arriving while it is
 * in flight get the same result. Once it completes, the key is released and the next call loads again.
 * <p>
 * Use it only for idempotent reads. The loader runs on an executor thread WITHOUT the caller's
 * SecurityContext, so the key must include every input the result depends on (user id included, if the
 * result is user-specific), and the loader must not read the SecurityContext itself.
 * <p>
 * Registered by {@link volumet.project.configs.RequestCoalescerExecutorConfig} (autoconfiguration),
 * not by component scanning, so every app using common-resilience gets it by adding the dependency.
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RequestCoalescer {
    // initialized here, so not a constructor parameter
    ConcurrentMap<VersionedKey, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    Executor executor;
    RequestCoalescerExecutorProperties properties;

    /** Blocking variant using the configured default timeout. */
    public <T> T execute(VersionedKey key, Supplier<? extends  T> loader) {
        return execute(key, loader, properties.getDefaultTimeout());
    }

    /**
     * Returns a future for the (possibly shared) load. Each caller gets its own copy, so cancelling it does
     * not affect other callers.
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> submit(VersionedKey key, Supplier<? extends T> loader) {
        CompletableFuture<Object> leader = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(key, leader);
        if (existing != null) {
            // a copy, never the shared future: a follower cancelling (e.g. on timeout) must not cancel the load
            // for everyone. Using `existing` (not a second map lookup) also avoids a null if the load just finished.
            return (CompletableFuture<T>) existing.copy();
        }

        try {
            CompletableFuture.supplyAsync(loader, executor).whenComplete((result, error) -> {
                try {
                    if (error != null) {
                        leader.completeExceptionally(error);
                    }
                    else {
                        leader.complete(result);
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

    /**
     * Blocking variant for request threads. Rethrows the loader's RuntimeException unchanged so the central
     * exception handler maps it as if it had been thrown directly.
     */
    public <T> T execute(VersionedKey key, Supplier<? extends T> loader, Duration timeout) {
        CompletableFuture<T> future = submit(key, loader);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(false);
            throw new CoalescedRequestTimeoutException(key, timeout);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for coalesced request " + key, ex);
        } catch (ExecutionException ex) {
            Throwable cause = unwrap(ex);
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Coalesced request " + key + " failed", cause);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
