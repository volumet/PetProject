package volumet.project.aspects;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import volumet.project.dtos.ErrorResponseDto;
import volumet.project.exceptions.integration.CoalescedRequestTimeoutException;

import static volumet.project.constants.ErrorCode.FORBIDDEN;
import static volumet.project.constants.ErrorCode.INTERNAL_ERROR;
import static volumet.project.constants.ErrorCode.SERVICE_UNAVAILABLE;
import static volumet.project.constants.ErrorCode.UNAUTHORIZED;
import static volumet.project.constants.ErrorCode.UPSTREAM_TIMEOUT;
import static volumet.project.utils.CorrelationUtils.getCorrelationId;


@RestControllerAdvice
public class GeneralExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleException(Exception ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                INTERNAL_ERROR,
                "Internal server error",
                getCorrelationId()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDto> authenticationException(AuthenticationException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                UNAUTHORIZED,
                "Authentication required",
                getCorrelationId()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> accessDeniedException(AccessDeniedException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                FORBIDDEN,
                "Access denied",
                getCorrelationId()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error);
    }

    /** Circuit breaker open, or every bulkhead slot busy: the protected dependency is shedding load. */
    @ExceptionHandler({CallNotPermittedException.class, BulkheadFullException.class})
    public ResponseEntity<ErrorResponseDto> serviceUnavailable(RuntimeException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                SERVICE_UNAVAILABLE,
                "Service temporarily unavailable, please retry shortly",
                getCorrelationId()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(error);
    }

    /** A coalesced load did not finish within the caller's timeout. */
    @ExceptionHandler(CoalescedRequestTimeoutException.class)
    public ResponseEntity<ErrorResponseDto> coalescedTimeout(CoalescedRequestTimeoutException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                UPSTREAM_TIMEOUT,
                "Request timed out",
                getCorrelationId()
        );

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(error);
    }
}
