package volumet.project.controller.v1;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import volumet.project.constants.ApiPath;
import volumet.project.dtos.request.PersonCreationRequestDto;
import volumet.project.services.UserService;

@RestController(value = "userRegistration")
@RequestMapping(ApiPath.API_V1 + "/registration")
@Validated
@RequiredArgsConstructor
public class UserRegistrationRestController {
    private final UserService userService;

    @PostMapping("/registerUser")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerUser(@Valid @RequestBody PersonCreationRequestDto request) {
        userService.createUser(request);
    }
}
