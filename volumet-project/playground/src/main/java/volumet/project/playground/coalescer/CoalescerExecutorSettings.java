package volumet.project.playground.coalescer;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings for {@link CoalescerTaskExecutor} (in production these come from
 * {@code volumet.executor.request-coalescer.*}).
 *
 * @param threadNamePrefix  worker threads are named {@code <prefix><n>}, n = 1, 2, 3, ...; must not be blank
 * @param concurrencyLimit  max tasks running at once; {@code -1} = unlimited; {@code 0} or below -1 is invalid
 * @param acquireTimeout    how long {@code execute} waits for a free slot before rejecting the task
 * @param awaitTermination  how long {@code close()} waits for running tasks before interrupting them
 */
public record CoalescerExecutorSettings(String threadNamePrefix,
                                        int concurrencyLimit,
                                        Duration acquireTimeout,
                                        Duration awaitTermination) {

    public static final int UNLIMITED = -1;

    public CoalescerExecutorSettings {
        Objects.requireNonNull(threadNamePrefix);
        if (threadNamePrefix.isBlank()) {
            throw new IllegalArgumentException("Thread name prefix must not be blank");
        }
        if (concurrencyLimit == 0 || concurrencyLimit < -1) {
            throw new IllegalArgumentException("Concurrency limit must not be 0 or less than -1");
        }
        Objects.requireNonNull(acquireTimeout);
        if (acquireTimeout.isNegative()) {
            throw new IllegalArgumentException("Negative timeout duration");
        }

        Objects.requireNonNull(awaitTermination);
        if (awaitTermination.isNegative()) {
            throw new IllegalArgumentException("Negative await duration");
        }
    }
}
