package volumet.project.converters;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static volumet.project.constants.SecurityConstant.REALM_ACCESS;
import static volumet.project.constants.SecurityConstant.ROLE_CLAIMS;
import static volumet.project.constants.SecurityConstant.ROLE_PREFIX;

@RequiredArgsConstructor
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(REALM_ACCESS);
        Set<GrantedAuthority> authorities = new HashSet<>();

        if (realmAccess != null && realmAccess.get(ROLE_CLAIMS) != null) {
            @SuppressWarnings("unchecked")
            Collection<String> roles = (Collection<String>) realmAccess.get(ROLE_CLAIMS);
            authorities.addAll(roles.stream()
                    .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                    .collect(Collectors.toSet()));
        }

        return new JwtAuthenticationToken(jwt, authorities);
    }
}