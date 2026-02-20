package volumet.project.services;

import volumet.project.dtos.request.PersonCreationRequestDto;

public interface UserService {
    void createUser(PersonCreationRequestDto personCreationRequestDto);
}
