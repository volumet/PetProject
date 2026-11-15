package volumet.project.configs;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Method security ({@code @PreAuthorize}, ...) is always on, independent of any profile or property:
 * no configuration can silently turn authorization checks into no-ops.
 */
@AutoConfiguration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
