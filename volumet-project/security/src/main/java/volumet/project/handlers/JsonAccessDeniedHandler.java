package volumet.project.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import volumet.project.utils.PermissionUtils;

import java.io.IOException;

/**
 * 403 with the {@code WWW-Authenticate: Bearer error="insufficient_scope"} header (Spring's
 * {@link BearerTokenAccessDeniedHandler}, RFC 6750) and the usual JSON error body.
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JsonAccessDeniedHandler implements AccessDeniedHandler {
    BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
    ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        bearer.handle(request, response, exception);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                PermissionUtils.buildErrorResponse("Access denied", HttpStatus.FORBIDDEN));
    }
}
