package volumet.project.configs.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "volumet.persistence.id")
@Getter
@Setter
public class DefaultIdGenerationProperties {
    private String namingStrategy = "volumet.project.generator.EntitySequenceNamingStrategy";

    private String optimizerPreferred = "pooled-lo";

    private int incrementSize = 50;
}
