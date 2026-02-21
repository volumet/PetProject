package volumet.project.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
public class UserIdentifierFilter extends OncePerRequestFilter implements PostSecurityFilter {
    private final DomainIdentifierResolver domainIdentifierResolver;
    private final AuthenticationFacade authenticationFacade;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        Optional<JwtAuthenticationToken> jwtAuthHolder = authenticationFacade.getJwtAuthentication();

        if (jwtAuthHolder.isPresent()) {
            JwtAuthenticationToken jwtAuth = jwtAuthHolder.get();
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