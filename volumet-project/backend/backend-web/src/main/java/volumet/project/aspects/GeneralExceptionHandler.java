package volumet.project.aspects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import volumet.project.dtos.ErrorResponseDto;

import static volumet.project.constants.ErrorCode.FORBIDDEN;
import static volumet.project.constants.ErrorCode.INTERNAL_ERROR;
import static volumet.project.constants.ErrorCode.UNAUTHORIZED;
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
}
