package volumet.project.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RoomCreationRequestDto(
        @NotNull @Positive Integer size,
        @NotBlank String roomName
) {
}
