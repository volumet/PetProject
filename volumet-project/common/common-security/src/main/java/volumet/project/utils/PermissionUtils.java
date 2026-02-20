package volumet.project.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import volumet.project.dtos.ErrorResponseDto;

import static volumet.project.utils.CorrelationUtils.getCorrelationId;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PermissionUtils {
    public static ErrorResponseDto buildErrorResponse(String message, HttpStatus status) {
        return new ErrorResponseDto(message, status.getReasonPhrase(), getCorrelationId());
    }
}
