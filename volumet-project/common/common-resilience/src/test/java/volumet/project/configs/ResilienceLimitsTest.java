package volumet.project.configs;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import volumet.project.components.ResilienceLimitsValidator;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared defaults + startup boundaries, with resilience4j's real Spring Boot auto-configuration.
 */
class ResilienceLimitsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> new ResilienceDefaultsEnvironmentPostProcessor()
                    .postProcessEnvironment(context.getEnvironment(), null))
            .withConfiguration(AutoConfigurations.of(
                    ValidationAutoConfiguration.class,
                    RetryAutoConfiguration.class,
                    CircuitBreakerAutoConfiguration.class,
                    BulkheadAutoConfiguration.class,
                    TimeLimiterAutoConfiguration.class,
                    RateLimiterAutoConfiguration.class,
                    RequestCoalescerExecutorConfig.class,
                    ResilienceLimitsConfig.class));

    // ---- shared defaults ----

    @Test
    void sharedDefaultsApplyAndStayWithinLimits() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RetryRegistry.class).getDefaultConfig().getMaxAttempts()).isEqualTo(3);
            assertThat(context.getBean(CircuitBreakerRegistry.class).getDefaultConfig().getMinimumNumberOfCalls()).isEqualTo(20);
            assertThat(context.getBean(BulkheadRegistry.class).getDefaultConfig().getMaxConcurrentCalls()).isEqualTo(10);
            assertThat(context.getBean(TimeLimiterRegistry.class).getDefaultConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(5));
            assertThat(context.getBean(ResilienceLimitsValidator.class).validate()).isEmpty();
        });
    }

    @Test
    void defaultRetryBacksOffWithJitterWithinTheTotalWaitLimit() {
        runner.run(context -> {
            Duration worst = ResilienceLimitsValidator.worstCaseTotalWait(context.getBean(RetryRegistry.class).getDefaultConfig());
            // ~200ms then ~400ms, each +-50%
            assertThat(worst).isBetween(Duration.ofMillis(600), Duration.ofMillis(900));
        });
    }

    @Test
    void instancesInheritTheSharedDefault() {
        runner.withPropertyValues("resilience4j.circuitbreaker.instances.orders.failure-rate-threshold=40")
                .run(context -> {
                    var config = context.getBean(CircuitBreakerRegistry.class).circuitBreaker("orders").getCircuitBreakerConfig();
                    assertThat(config.getFailureRateThreshold()).isEqualTo(40f);          // instance value
                    assertThat(config.getMinimumNumberOfCalls()).isEqualTo(20);           // inherited
                    assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(20_000L);
                });
    }

    @Test
    void appCanOverrideASharedDefaultKey() {
        runner.withPropertyValues("resilience4j.retry.configs.default.max-attempts=2")
                .run(context -> assertThat(context.getBean(RetryRegistry.class).getDefaultConfig().getMaxAttempts()).isEqualTo(2));
    }

    // ---- boundaries ----

    @Test
    void tooManyRetryAttemptsFailsStartup() {
        runner.withPropertyValues("resilience4j.retry.instances.payments.max-attempts=5")
                .run(context -> assertThat(context).getFailure()
                        .hasMessageContaining("retry 'payments': max-attempts 5 > 3"));
    }

    @Test
    void tooMuchTotalRetryWaitFailsStartup() {
        runner.withPropertyValues(
                        "resilience4j.retry.instances.payments.enable-randomized-wait=false",
                        "resilience4j.retry.instances.payments.wait-duration=1500ms",
                        "resilience4j.retry.instances.payments.exponential-max-wait-duration=5s")
                .run(context -> assertThat(context).getFailure()
                        .hasMessageContaining("retry 'payments': worst-case total wait between attempts PT4.5S > PT2S"));
    }

    @Test
    void retriesOutlastingTheCoalescerTimeoutFailStartup() {
        runner.withPropertyValues(
                        "volumet.executor.request-coalescer.default-timeout=500ms",
                        "resilience4j.retry.configs.default.enable-randomized-wait=false")
                .run(context -> assertThat(context).getFailure()
                        .hasMessageContaining("retry 'default': worst-case total wait PT0.6S >= request coalescer default-timeout PT0.5S"));
    }

    @Test
    void circuitBreakerOpeningOnTooFewCallsFailsStartup() {
        runner.withPropertyValues("resilience4j.circuitbreaker.instances.orders.minimum-number-of-calls=1")
                .run(context -> assertThat(context).getFailure()
                        .hasMessageContaining("circuit breaker 'orders': minimum-number-of-calls 1 < 5"));
    }

    @Test
    void circuitBreakerOpenStateOutsideBoundsFailsStartup() {
        runner.withPropertyValues(
                        "resilience4j.circuitbreaker.instances.tooShort.wait-duration-in-open-state=1s",
                        "resilience4j.circuitbreaker.instances.tooLong.wait-duration-in-open-state=10m")
                .run(context -> assertThat(context).getFailure()
                        .hasMessageContaining("circuit breaker 'tooShort': wait-duration-in-open-state PT1S outside [PT5S, PT1M]")
                        .hasMessageContaining("circuit breaker 'tooLong': wait-duration-in-open-state PT10M outside [PT5S, PT1M]"));
    }

    @Test
    void circuitBreakerFailureRateOutsideBoundsFailsStartup() {
        runner.withPropertyValues("resilience4j.circuitbreaker.instances.orders.failure-rate-threshold=100")
                .run(context -> assertThat(context).getFailure()
                        .hasMessageContaining("circuit breaker 'orders': failure-rate-threshold 100.0 outside [10.0, 90.0]"));
    }

    @Test
    void oversizedBulkheadFailsStartup() {
        runner.withPropertyValues(
                        "resilience4j.bulkhead.instances.reports.max-concurrent-calls=200",
                        "resilience4j.bulkhead.instances.reports.max-wait-duration=5s")
                .run(context -> assertThat(context).getFailure()
                        .hasMessageContaining("bulkhead 'reports': max-concurrent-calls 200 > 50")
                        .hasMessageContaining("bulkhead 'reports': max-wait-duration PT5S > PT1S"));
    }

    @Test
    void longTimeLimiterAndRateLimiterWaitFailStartup() {
        runner.withPropertyValues(
                        "resilience4j.timelimiter.instances.export.timeout-duration=30s",
                        "resilience4j.ratelimiter.instances.login.timeout-duration=5s")
                .run(context -> assertThat(context).getFailure()
                        .hasMessageContaining("time limiter 'export': timeout-duration PT30S > PT10S")
                        .hasMessageContaining("rate limiter 'login': timeout-duration PT5S > PT1S"));
    }

    @Test
    void appCanRaiseALimitDeliberately() {
        runner.withPropertyValues(
                        "volumet.resilience.limits.retry.max-attempts=5",
                        "volumet.resilience.limits.retry.max-total-wait=5s",
                        "resilience4j.retry.instances.payments.max-attempts=5")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void checkCanBeDisabled() {
        runner.withPropertyValues(
                        "volumet.resilience.limits.enabled=false",
                        "resilience4j.retry.instances.payments.max-attempts=10")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ResilienceLimitsValidator.class);
                });
    }
}
