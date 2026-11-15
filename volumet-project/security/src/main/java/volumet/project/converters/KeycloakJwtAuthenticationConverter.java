package volumet.project.converters;

import lombok.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static volumet.project.constants.SecurityConstant.REALM_ACCESS;
import static volumet.project.constants.SecurityConstant.RESOURCE_ACCESS;
import static volumet.project.constants.SecurityConstant.ROLE_CLAIMS;
import static volumet.project.constants.SecurityConstant.ROLE_PREFIX;

/**
 * Keycloak access token → authorities:
 * <ul>
 *   <li>realm roles ({@code realm_access.roles}) → {@code ROLE_<role>}</li>
 *   <li>this service's client roles ({@code resource_access.<clientId>.roles}) → {@code ROLE_<role>}, when a
 *       client id is configured (volumet.security.keycloak.client-id); other clients' roles are ignored</li>
 *   <li>scopes ({@code scope}) → {@code SCOPE_<scope>}, as Spring does by default</li>
 * </ul>
 * Claims of an unexpected shape are ignored instead of failing the request with a ClassCastException.
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final String clientId;
    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    /** @param clientId this service's Keycloak client id, or null to use realm roles only */
    public KeycloakJwtAuthenticationConverter(String clientId) {
        this.clientId = clientId == null || clientId.isBlank() ? null : clientId;
    }

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        addRoles(authorities, jwt.getClaim(REALM_ACCESS));
        if (clientId != null && jwt.getClaim(RESOURCE_ACCESS) instanceof Map<?, ?> resourceAccess) {
            addRoles(authorities, resourceAccess.get(clientId));
        }
        authorities.addAll(scopes.convert(jwt));
        return new JwtAuthenticationToken(jwt, authorities);
    }

    /** Adds {@code roles} of a {@code {"roles": [...]}} claim object; anything else is ignored. */
    private static void addRoles(Set<GrantedAuthority> authorities, Object access) {
        if (access instanceof Map<?, ?> map && map.get(ROLE_CLAIMS) instanceof Collection<?> roles) {
            for (Object role : roles) {
                if (role instanceof String name && !name.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + name));
                }
            }
        }
    }
}
