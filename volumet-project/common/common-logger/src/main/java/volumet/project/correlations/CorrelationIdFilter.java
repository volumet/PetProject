package volumet.project.correlations;

import volumet.project.constants.CorrelationIdConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CorrelationIdConstant.CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CorrelationIdConstant.MDC_KEY, correlationId);
        response.setHeader(CorrelationIdConstant.CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        }
        finally {
            MDC.clear();
        }
    }
}