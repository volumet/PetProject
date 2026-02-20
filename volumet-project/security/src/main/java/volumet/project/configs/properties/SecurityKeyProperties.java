package volumet.project.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record SecurityKeyProperties(String secretKey) {
}
