package volumet.project.configs.properties;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "volumet.security")
@Validated
@Getter
@Setter
public class VolumetSecurityProperties {

    @NestedConfigurationProperty
    private Keycloak keycloak = new Keycloak();

    @NestedConfigurationProperty
    private Cors cors = new Cors();

    @NestedConfigurationProperty
    private Actuator actuator = new Actuator();

    @Getter
    @Setter
    public static class Actuator {
        /**
         * Actuator endpoint ids reachable without a token (each with its sub-paths, e.g. health/liveness).
         * Every other actuator endpoint needs ROLE_ADMIN. Add "prometheus" when a scraper can't send a token.
         */
        private List<String> publicEndpoints = new ArrayList<>(List.of("health", "info"));
    }

    @Getter
    @Setter
    public static class Keycloak {
        /**
         * This service's Keycloak client id. When set, the client roles in {@code resource_access.<clientId>.roles}
         * become ROLE_ authorities as well as the realm roles. Roles of other clients are never used.
         */
        private String clientId;
    }

    /**
     * CORS for browser frontends on other origins. Off unless {@code allowed-origins} is set.
     */
    @Getter
    @Setter
    public static class Cors {
        /** Exact origins, e.g. https://app.example.com. Empty = CORS disabled (same-origin and non-browser clients only). */
        private List<String> allowedOrigins = new ArrayList<>();

        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        private List<String> allowedHeaders = new ArrayList<>(List.of("Authorization", "Content-Type", "X-Correlation-ID"));

        /** Response headers the browser may read. */
        private List<String> exposedHeaders = new ArrayList<>(List.of("X-Correlation-ID", "WWW-Authenticate"));

        /** How long browsers may cache a preflight response. */
        @NotNull
        private Duration maxAge = Duration.ofHours(1);
    }
}
