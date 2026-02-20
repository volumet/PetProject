package volumet.project.configs;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import volumet.project.correlations.CorrelationIdFilter;

@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(
        prefix = "app.correlation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CorrelationIdConfig {
    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegister(CorrelationIdFilter correlationIdFilter) {
        FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegister = new FilterRegistrationBean<>();
        correlationIdFilterRegister.setFilter(correlationIdFilter);
        correlationIdFilterRegister.setOrder(Ordered.HIGHEST_PRECEDENCE); // To populate correlation id for unauthenticated cases
        return correlationIdFilterRegister;
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}