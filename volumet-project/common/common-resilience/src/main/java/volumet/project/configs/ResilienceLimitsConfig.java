package volumet.project.configs;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import volumet.project.components.ResilienceLimitsValidator;
import volumet.project.configs.properties.RequestCoalescerExecutorProperties;
import volumet.project.configs.properties.ResilienceLimitsProperties;

/**
 * Auto-configuration (common-resilience): startup check that every resilience4j config stays within
 * volumet.resilience.limits.*. Turn off with volumet.resilience.limits.enabled=false.
 */
@AutoConfiguration(after = RequestCoalescerExecutorConfig.class)
@ConditionalOnProperty(prefix = "volumet.resilience.limits", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ResilienceLimitsProperties.class)
public class ResilienceLimitsConfig {

    @Bean
    @ConditionalOnMissingBean
    public ResilienceLimitsValidator resilienceLimitsValidator(ResilienceLimitsProperties limits,
                                                               ObjectProvider<RetryRegistry> retries,
                                                               ObjectProvider<CircuitBreakerRegistry> circuitBreakers,
                                                               ObjectProvider<BulkheadRegistry> bulkheads,
                                                               ObjectProvider<TimeLimiterRegistry> timeLimiters,
                                                               ObjectProvider<RateLimiterRegistry> rateLimiters,
                                                               ObjectProvider<RequestCoalescerExecutorProperties> coalescer) {
        return new ResilienceLimitsValidator(limits, retries, circuitBreakers, bulkheads, timeLimiters, rateLimiters, coalescer);
    }
}
