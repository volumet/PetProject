package volumet.project.configs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Adds common-resilience's shared resilience4j defaults ({@value #DEFAULTS}) as the lowest-precedence property
 * source, so every service starts from the same safe values and overrides only what it needs in application.yml.
 * Registered in META-INF/spring.factories.
 */
public class ResilienceDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String DEFAULTS = "volumet/resilience4j-defaults.yml";
    static final String PROPERTY_SOURCE_NAME = "volumet-resilience4j-defaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Resource resource = new ClassPathResource(DEFAULTS, getClass().getClassLoader());
        try {
            for (PropertySource<?> source : new YamlPropertySourceLoader().load(PROPERTY_SOURCE_NAME, resource)) {
                environment.getPropertySources().addLast(source);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot load " + DEFAULTS, ex);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
