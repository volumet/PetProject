package volumet.project.configs;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import volumet.project.components.DomainIdentifierResolver;
import volumet.project.configs.properties.VolumetSecurityProperties;
import volumet.project.contexts.AuthenticationFacade;
import volumet.project.converters.KeycloakJwtAuthenticationConverter;
import volumet.project.filters.UserIdentifierFilter;

@AutoConfiguration
@EnableConfigurationProperties(VolumetSecurityProperties.class)
public class AuthenticationConfig {
    public static final String JWT_AUTHENTICATION_CONVERTER = "jwtAuthenticationConverter";

    /**
     * Keycloak roles → Spring authorities, in every environment (the service is always a Keycloak resource server).
     * <p>
     * A concrete class rather than a lambda: Spring MVC can't resolve a lambda Converter's generic types.
     * Backs off only for a bean with the same name: an app replaces it by declaring its own
     * {@value #JWT_AUTHENTICATION_CONVERTER}. Unrelated Converter beans (e.g. MVC String converters) don't count.
     */
    @Bean(name = JWT_AUTHENTICATION_CONVERTER)
    @ConditionalOnMissingBean(name = JWT_AUTHENTICATION_CONVERTER)
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(VolumetSecurityProperties properties) {
        return new KeycloakJwtAuthenticationConverter(properties.getKeycloak().getClientId());
    }

    @Bean
    @ConditionalOnMissingBean
    AuthenticationFacade authenticationFacade() {
        return new AuthenticationFacade();
    }

    /**
     * Maps the Keycloak user (iss + sub) to the app's own user uuid (AppPrincipal) after JWT authentication.
     * Active only when the app provides a DomainIdentifierResolver (its own lookup/creation of the user).
     * Added to the security chain by SecurityConfig (PostSecurityFilter, after BearerTokenAuthenticationFilter).
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(DomainIdentifierResolver.class)
    @ConditionalOnMissingBean
    UserIdentifierFilter userIdentifierFilter(DomainIdentifierResolver domainIdentifierResolver,
                                              AuthenticationFacade authenticationFacade) {
        return new UserIdentifierFilter(domainIdentifierResolver, authenticationFacade);
    }

    /**
     * Spring Boot registers every Filter bean as a servlet filter too. Disable that, so the filter only runs
     * inside the security chain, after the JWT has been authenticated. Otherwise a servlet-level run before
     * authentication would mark the request as filtered and skip the real run: no AppPrincipal, and every
     * audit column silently becomes SYSTEM.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(DomainIdentifierResolver.class)
    FilterRegistrationBean<UserIdentifierFilter> userIdentifierFilterServletRegistration(UserIdentifierFilter filter) {
        FilterRegistrationBean<UserIdentifierFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}