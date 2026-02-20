package volumet.project.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import volumet.project.dtos.AppPrincipal;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecurityUserUtil {
    public static UUID getCurrentSubject() {
        Authentication authentication = getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            AppPrincipal principal = (AppPrincipal) jwtAuth.getPrincipal();
            return principal.getUuid();
        }
        throw new AuthenticationCredentialsNotFoundException("Principal not found exception");
    }

    private static Authentication getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("No authentication found in Security Context");
        }
        return authentication;
    }
}
