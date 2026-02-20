package volumet.project.configs;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import volumet.project.annotations.KeyCloakProfile;
import volumet.project.components.DomainIdentifierResolver;
import volumet.project.converters.KeycloakJwtAuthenticationConverter;

@AutoConfiguration
public class AuthenticationConfig {
    /**
     * Returning a concrete class to avoid clashing with SpringMvc internal mechanism
     */
    @Bean
    @KeyCloakProfile
    @ConditionalOnMissingBean(Converter.class)
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(DomainIdentifierResolver domainIdentifierResolver) {
        return new KeycloakJwtAuthenticationConverter(domainIdentifierResolver);
    }
}
