package volumet.project.components;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import volumet.project.configs.properties.RequestCoalescerExecutorProperties;
import volumet.project.configs.properties.ResilienceLimitsProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Checks, once all beans exist, that every resilience4j config (each registry's {@code default} config and every
 * configured instance) stays within {@link ResilienceLimitsProperties}. Fails startup listing every violation.
 * <p>
 * Instances created later at runtime (an annotation naming an instance that isn't in the yml) use the registry's
 * default config, which is checked here too.
 */
@Slf4j
public class ResilienceLimitsValidator implements SmartInitializingSingleton {
    /** Samples per retry attempt when estimating randomized waits (worst case wins). */
    private static final int RANDOMIZED_WAIT_SAMPLES = 50;

    private final ResilienceLimitsProperties limits;
    private final ObjectProvider<RetryRegistry> retries;
    private final ObjectProvider<CircuitBreakerRegistry> circuitBreakers;
    private final ObjectProvider<BulkheadRegistry> bulkheads;
    private final ObjectProvider<TimeLimiterRegistry> timeLimiters;
    private final ObjectProvider<RateLimiterRegistry> rateLimiters;
    private final ObjectProvider<RequestCoalescerExecutorProperties> coalescer;

    public ResilienceLimitsValidator(ResilienceLimitsProperties limits,
                                     ObjectProvider<RetryRegistry> retries,
                                     ObjectProvider<CircuitBreakerRegistry> circuitBreakers,
                                     ObjectProvider<BulkheadRegistry> bulkheads,
                                     ObjectProvider<TimeLimiterRegistry> timeLimiters,
                                     ObjectProvider<RateLimiterRegistry> rateLimiters,
                                     ObjectProvider<RequestCoalescerExecutorProperties> coalescer) {
        this.limits = limits;
        this.retries = retries;
        this.circuitBreakers = circuitBreakers;
        this.bulkheads = bulkheads;
        this.timeLimiters = timeLimiters;
        this.rateLimiters = rateLimiters;
        this.coalescer = coalescer;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> violations = validate();
        if (!violations.isEmpty()) {
            throw new IllegalStateException("resilience4j configuration exceeds volumet.resilience.limits:\n  - "
                    + String.join("\n  - ", violations));
        }
    }

    /** All violations, empty if everything is within limits. */
    public List<String> validate() {
        List<String> violations = new ArrayList<>();
        retries.ifAvailable(registry -> {
            checkRetry("default", registry.getDefaultConfig(), violations);
            registry.getAllRetries().forEach(r -> checkRetry(r.getName(), r.getRetryConfig(), violations));
        });
        circuitBreakers.ifAvailable(registry -> {
            checkCircuitBreaker("default", registry.getDefaultConfig(), violations);
            registry.getAllCircuitBreakers().forEach(cb -> checkCircuitBreaker(cb.getName(), cb.getCircuitBreakerConfig(), violations));
        });
        bulkheads.ifAvailable(registry -> {
            checkBulkhead("default", registry.getDefaultConfig(), violations);
            registry.getAllBulkheads().forEach(b -> checkBulkhead(b.getName(), b.getBulkheadConfig(), violations));
        });
        timeLimiters.ifAvailable(registry -> {
            checkTimeLimiter("default", registry.getDefaultConfig(), violations);
            registry.getAllTimeLimiters().forEach(t -> checkTimeLimiter(t.getName(), t.getTimeLimiterConfig(), violations));
        });
        rateLimiters.ifAvailable(registry -> {
            checkRateLimiter("default", registry.getDefaultConfig(), violations);
            registry.getAllRateLimiters().forEach(r -> checkRateLimiter(r.getName(), r.getRateLimiterConfig(), violations));
        });
        return violations;
    }

    private void checkRetry(String name, RetryConfig config, List<String> violations) {
        ResilienceLimitsProperties.Retry limit = limits.getRetry();
        BiConsumer<String, Object[]> fail = (message, args) -> violations.add("retry '" + name + "': " + message.formatted(args));

        if (config.getMaxAttempts() > limit.getMaxAttempts()) {
            fail.accept("max-attempts %d > %d (retry.max-attempts)", new Object[]{config.getMaxAttempts(), limit.getMaxAttempts()});
        }
        Duration totalWait = worstCaseTotalWait(config);
        if (totalWait.compareTo(limit.getMaxTotalWait()) > 0) {
            fail.accept("worst-case total wait between attempts %s > %s (retry.max-total-wait)", new Object[]{totalWait, limit.getMaxTotalWait()});
        }
        coalescer.ifAvailable(properties -> {
            if (totalWait.compareTo(properties.getDefaultTimeout()) >= 0) {
                fail.accept("worst-case total wait %s >= request coalescer default-timeout %s: coalesced callers would time out before the retries finish",
                        new Object[]{totalWait, properties.getDefaultTimeout()});
            }
        });
    }

    private void checkCircuitBreaker(String name, CircuitBreakerConfig config, List<String> violations) {
        ResilienceLimitsProperties.CircuitBreaker limit = limits.getCircuitBreaker();
        String prefix = "circuit breaker '" + name + "': ";

        if (config.getMinimumNumberOfCalls() < limit.getMinMinimumNumberOfCalls()) {
            violations.add(prefix + "minimum-number-of-calls %d < %d (circuit-breaker.min-minimum-number-of-calls)"
                    .formatted(config.getMinimumNumberOfCalls(), limit.getMinMinimumNumberOfCalls()));
        }
        float failureRate = config.getFailureRateThreshold();
        if (failureRate < limit.getMinFailureRateThreshold() || failureRate > limit.getMaxFailureRateThreshold()) {
            violations.add(prefix + "failure-rate-threshold %s outside [%s, %s] (circuit-breaker.min/max-failure-rate-threshold)"
                    .formatted(failureRate, limit.getMinFailureRateThreshold(), limit.getMaxFailureRateThreshold()));
        }
        Duration open = Duration.ofMillis(config.getWaitIntervalFunctionInOpenState().apply(1));
        if (open.compareTo(limit.getMinWaitInOpenState()) < 0 || open.compareTo(limit.getMaxWaitInOpenState()) > 0) {
            violations.add(prefix + "wait-duration-in-open-state %s outside [%s, %s] (circuit-breaker.min/max-wait-in-open-state)"
                    .formatted(open, limit.getMinWaitInOpenState(), limit.getMaxWaitInOpenState()));
        }
        if (config.getSlowCallDurationThreshold().compareTo(limit.getMaxSlowCallDurationThreshold()) > 0) {
            violations.add(prefix + "slow-call-duration-threshold %s > %s (circuit-breaker.max-slow-call-duration-threshold)"
                    .formatted(config.getSlowCallDurationThreshold(), limit.getMaxSlowCallDurationThreshold()));
        }
    }

    private void checkBulkhead(String name, BulkheadConfig config, List<String> violations) {
        ResilienceLimitsProperties.Bulkhead limit = limits.getBulkhead();
        if (config.getMaxConcurrentCalls() > limit.getMaxConcurrentCalls()) {
            violations.add("bulkhead '%s': max-concurrent-calls %d > %d (bulkhead.max-concurrent-calls)"
                    .formatted(name, config.getMaxConcurrentCalls(), limit.getMaxConcurrentCalls()));
        }
        if (config.getMaxWaitDuration().compareTo(limit.getMaxWaitDuration()) > 0) {
            violations.add("bulkhead '%s': max-wait-duration %s > %s (bulkhead.max-wait-duration)"
                    .formatted(name, config.getMaxWaitDuration(), limit.getMaxWaitDuration()));
        }
    }

    private void checkTimeLimiter(String name, TimeLimiterConfig config, List<String> violations) {
        if (config.getTimeoutDuration().compareTo(limits.getTimeLimiter().getMaxTimeout()) > 0) {
            violations.add("time limiter '%s': timeout-duration %s > %s (time-limiter.max-timeout)"
                    .formatted(name, config.getTimeoutDuration(), limits.getTimeLimiter().getMaxTimeout()));
        }
    }

    private void checkRateLimiter(String name, RateLimiterConfig config, List<String> violations) {
        if (config.getTimeoutDuration().compareTo(limits.getRateLimiter().getMaxTimeout()) > 0) {
            violations.add("rate limiter '%s': timeout-duration %s > %s (rate-limiter.max-timeout)"
                    .formatted(name, config.getTimeoutDuration(), limits.getRateLimiter().getMaxTimeout()));
        }
    }

    /** Sum of the waits between attempts; randomized waits are sampled and the largest sample counts. */
    public static Duration worstCaseTotalWait(RetryConfig config) {
        long totalMillis = 0;
        Either<Throwable, Object> failure = Either.left(new RuntimeException("limit check"));
        for (int attempt = 1; attempt < config.getMaxAttempts(); attempt++) {
            long worst = 0;
            for (int sample = 0; sample < RANDOMIZED_WAIT_SAMPLES; sample++) {
                worst = Math.max(worst, config.getIntervalBiFunction().apply(attempt, failure));
            }
            totalMillis += worst;
        }
        return Duration.ofMillis(totalMillis);
    }
}
