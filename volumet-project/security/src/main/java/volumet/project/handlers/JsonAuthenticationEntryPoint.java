package volumet.project.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import volumet.project.utils.PermissionUtils;

import java.io.IOException;

/**
 * 401 for every authentication failure, missing and invalid/expired tokens alike:
 * <ul>
 *   <li>status and {@code WWW-Authenticate: Bearer ...} header from Spring's {@link BearerTokenAuthenticationEntryPoint}
 *       (RFC 6750: clients can tell "no token" from "invalid_token");</li>
 *   <li>the usual JSON error body with correlation id.</li>
 * </ul>
 * Registered on both the exception handling and the bearer-token filter, so no path returns an empty 401.
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
    BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        bearer.commence(request, response, exception);
        String message = exception instanceof OAuth2AuthenticationException
                ? "Invalid or expired token"
                : "Authentication required";
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                PermissionUtils.buildErrorResponse(message, HttpStatus.valueOf(response.getStatus())));
    }
}
