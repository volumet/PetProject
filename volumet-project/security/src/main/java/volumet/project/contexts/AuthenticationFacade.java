package volumet.project.contexts;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import volumet.project.dtos.AppPrincipal;

import java.util.Optional;

public class AuthenticationFacade {

    public Optional<JwtAuthenticationToken> getJwtAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwt && authentication.isAuthenticated()) {
            return Optional.of(jwt);
        }

        return Optional.empty();
    }

    public void enrichJwtAuthentication(AppPrincipal appPrincipal) {
        Optional<JwtAuthenticationToken> jwtAuthHolder = getJwtAuthentication();
        if (jwtAuthHolder.isPresent()) {
            JwtAuthenticationToken jwtAuth = jwtAuthHolder.get();
            Jwt jwt = jwtAuth.getToken();
            JwtAuthenticationToken enrichedToken = new JwtAuthenticationToken(jwt, jwtAuth.getAuthorities(), jwt.getSubject()) {
                @Override
                public Object getPrincipal() {
                    return appPrincipal;
                }
            };
            SecurityContextHolder.getContext().setAuthentication(enrichedToken);
        }
    }
}
