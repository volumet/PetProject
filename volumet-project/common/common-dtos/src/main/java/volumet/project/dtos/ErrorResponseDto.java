package volumet.project.dtos;

/** Body of every error response. */
public record ErrorResponseDto(String errorCode, String message, String correlationId) {
}
