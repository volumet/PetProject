package volumet.project.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import volumet.project.utils.SecurityUserUtil;

import java.util.Optional;

import static volumet.project.constants.SecurityConstants.SYSTEM_USER;

@Configuration
@EnableJpaAuditing(modifyOnCreate = false)
public class JpaAuditableConfig {
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            try {
                return Optional.of(SecurityUserUtil.getCurrentSubject().toString());
            }
            catch (AuthenticationCredentialsNotFoundException _) {
                return Optional.of(SYSTEM_USER);
            }
        };
    }
}
