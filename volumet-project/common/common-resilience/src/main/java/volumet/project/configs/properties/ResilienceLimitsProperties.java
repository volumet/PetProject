package volumet.project.configs.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Boundaries every resilience4j config in the app must stay within (the {@code default} configs and every
 * instance). Checked once at startup; any violation stops the app with the full list.
 * <p>
 * They exist because a single generous setting can turn protection into an outage amplifier: e.g. 5 retries
 * per call multiply load on a struggling DB by 5, and retries outlasting the coalescer timeout make every
 * caller time out anyway. Raise a limit only with a reason, per app, under {@code volumet.resilience.limits.*}.
 */
@ConfigurationProperties(prefix = "volumet.resilience.limits")
@Validated
@Getter
@Setter
public class ResilienceLimitsProperties {
    /** Turn the startup check off (not recommended outside experiments). */
    private boolean enabled = true;

    @NestedConfigurationProperty
    private Retry retry = new Retry();
    @NestedConfigurationProperty
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    @NestedConfigurationProperty
    private Bulkhead bulkhead = new Bulkhead();
    @NestedConfigurationProperty
    private TimeLimiter timeLimiter = new TimeLimiter();
    @NestedConfigurationProperty
    private RateLimiter rateLimiter = new RateLimiter();

    @Getter
    @Setter
    public static class Retry {
        /** Most attempts per call, the first one included (3 = 1 call + 2 retries). */
        @Min(1)
        private int maxAttempts = 3;

        /**
         * Most total time spent waiting between attempts of one call (worst case, randomization included).
         * Also has to stay below volumet.executor.request-coalescer.default-timeout, or coalesced callers
         * time out before the retries finish.
         */
        @NotNull
        private Duration maxTotalWait = Duration.ofSeconds(2);
    }

    @Getter
    @Setter
    public static class CircuitBreaker {
        /** Fewest calls before the failure rate counts: opening on 1-2 failed calls is noise, not a signal. */
        @Min(1)
        private int minMinimumNumberOfCalls = 5;

        @DecimalMin("1") @DecimalMax("100")
        private float minFailureRateThreshold = 10;

        @DecimalMin("1") @DecimalMax("100")
        private float maxFailureRateThreshold = 90;

        /** Shortest open state: shorter just hammers the dependency again before it can recover. */
        @NotNull
        private Duration minWaitInOpenState = Duration.ofSeconds(5);

        /** Longest open state: longer keeps the feature down well after the dependency recovered. */
        @NotNull
        private Duration maxWaitInOpenState = Duration.ofSeconds(60);

        @NotNull
        private Duration maxSlowCallDurationThreshold = Duration.ofSeconds(10);
    }

    @Getter
    @Setter
    public static class Bulkhead {
        /** Most concurrent calls; keep DB-backed bulkheads below the connection pool size. */
        @Min(1)
        private int maxConcurrentCalls = 50;

        /** Longest a call may queue for a slot; queuing longer only holds request threads. */
        @NotNull
        private Duration maxWaitDuration = Duration.ofSeconds(1);
    }

    @Getter
    @Setter
    public static class TimeLimiter {
        @NotNull
        private Duration maxTimeout = Duration.ofSeconds(10);
    }

    @Getter
    @Setter
    public static class RateLimiter {
        /** Longest a call may wait for a permit. */
        @NotNull
        private Duration maxTimeout = Duration.ofSeconds(1);
    }
}
