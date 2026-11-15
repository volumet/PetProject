package volumet.project.configs;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import volumet.project.configs.properties.DefaultIdGenerationProperties;

import static volumet.project.constants.PersistentConstant.HIBERNATE_ID_DB_STRUCTURE_NAMING_STRATEGY;
import static volumet.project.constants.PersistentConstant.HIBERNATE_ID_OPTIMIZER_INCREMENT_SIZE;
import static volumet.project.constants.PersistentConstant.HIBERNATE_ID_OPTIMIZER_POOLED_PREFERRED;

@AutoConfiguration
@EnableConfigurationProperties(DefaultIdGenerationProperties.class)
public class CommonPersistenceConfiguration {

    /**
     * Default properties for hibernate optimizer.
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(DefaultIdGenerationProperties properties) {
        return hibernateProperties -> {
            hibernateProperties.putIfAbsent(
                    HIBERNATE_ID_DB_STRUCTURE_NAMING_STRATEGY,
                    properties.getNamingStrategy()
            );
            hibernateProperties.putIfAbsent(
                    HIBERNATE_ID_OPTIMIZER_POOLED_PREFERRED,
                    properties.getOptimizerPreferred()
            );
            hibernateProperties.putIfAbsent(
                    HIBERNATE_ID_OPTIMIZER_INCREMENT_SIZE,
                    properties.getIncrementSize()
            );
        };
    }
}
