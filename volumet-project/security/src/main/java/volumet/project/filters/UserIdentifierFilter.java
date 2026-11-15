package volumet.project.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import volumet.project.components.DomainIdentifierResolver;
import volumet.project.components.PostSecurityFilter;
import volumet.project.contexts.AuthenticationFacade;
import volumet.project.dto.ExternalIdentifierDto;
import volumet.project.dtos.AppPrincipal;

import java.io.IOException;
import java.util.Optional;

import static volumet.project.constants.SecurityConstant.FAMILY_NAME;
import static volumet.project.constants.SecurityConstant.GIVEN_NAME;

/**
 * This filter picks up issuer and subject from jwt then search for related uuid. <br/>
 * If not found then create new user. <br/>
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserIdentifierFilter extends OncePerRequestFilter implements PostSecurityFilter {
    DomainIdentifierResolver domainIdentifierResolver;
    AuthenticationFacade authenticationFacade;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        Optional<JwtAuthenticationToken> jwtAuthHolder = authenticationFacade.getJwtAuthentication();

        if (jwtAuthHolder.isPresent()) {
            JwtAuthenticationToken jwtAuth = jwtAuthHolder.get();

            // Uuid is stored in the context then do nothing
            if (jwtAuth.getPrincipal() instanceof AppPrincipal) {
                filterChain.doFilter(request, response);
                return;
            }

            Jwt jwt = jwtAuth.getToken();
            String identifierUuid = domainIdentifierResolver.resolveIdentifierUuid(
                    new ExternalIdentifierDto(
                            jwt.getIssuer().toString(),
                            jwt.getSubject(),
                            jwt.getClaimAsString(GIVEN_NAME),
                            jwt.getClaimAsString(FAMILY_NAME)
                    )
            );
            AppPrincipal appPrincipal = new AppPrincipal(identifierUuid);

            authenticationFacade.enrichJwtAuthentication(appPrincipal);
        }

        filterChain.doFilter(request, response);
    }
}