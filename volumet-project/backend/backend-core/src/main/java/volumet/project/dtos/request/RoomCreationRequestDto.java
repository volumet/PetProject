package volumet.project.dtos.request;


import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class RoomCreationRequestDto {
    @NotBlank
    private Integer size;

    @NotBlank
    private String roomName;
}
