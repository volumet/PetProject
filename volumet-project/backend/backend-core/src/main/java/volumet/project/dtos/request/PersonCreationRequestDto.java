package volumet.project.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import volumet.project.annotations.Phone;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class PersonCreationRequestDto {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    private LocalDate dateOfBirth;

    private String socialId;

    @Email
    private String email;

    @Phone
    private String phoneNumber;
}
