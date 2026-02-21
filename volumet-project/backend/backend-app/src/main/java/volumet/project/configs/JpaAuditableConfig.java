package volumet.project.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import volumet.project.components.DomainIdentifierResolver;
import volumet.project.filters.UserIdentifierFilter;
import volumet.project.contexts.AuthenticationFacade;
import volumet.project.dtos.AppPrincipal;

import java.util.Optional;

import static volumet.project.constants.SecurityConstants.SYSTEM_USER;

@Configuration
@EnableJpaAuditing(modifyOnCreate = false)
public class JpaAuditableConfig {
    @Bean
    public AuditorAware<String> auditorAware(AuthenticationFacade authenticationFacade) {
        return () -> {
            try {
                Optional<JwtAuthenticationToken> jwtAuthHolder = authenticationFacade.getJwtAuthentication();
                if (jwtAuthHolder.isPresent()) {
                    JwtAuthenticationToken jwtAuth = jwtAuthHolder.get();

                    Object principal = jwtAuth.getPrincipal();
                    if (principal instanceof AppPrincipal appPrincipal) {
                        return Optional.of(appPrincipal.getUuid());
                    }
                }
                return Optional.of(SYSTEM_USER);
            }
            catch (AuthenticationCredentialsNotFoundException _) {
                return Optional.of(SYSTEM_USER);
            }
        };
    }

    @Bean
    public UserIdentifierFilter userIdentifierFilerChain(DomainIdentifierResolver domainIdentifierResolver, AuthenticationFacade authenticationFacade) {
        return new UserIdentifierFilter(domainIdentifierResolver, authenticationFacade);
    }
}