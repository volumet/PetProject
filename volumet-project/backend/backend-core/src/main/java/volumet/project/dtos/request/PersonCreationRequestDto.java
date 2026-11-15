package volumet.project.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import volumet.project.annotations.Phone;

import java.time.LocalDate;

public record PersonCreationRequestDto(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull @Past LocalDate dateOfBirth,
        String socialId,
        @Email String email,
        @Phone String phoneNumber
) {
}
