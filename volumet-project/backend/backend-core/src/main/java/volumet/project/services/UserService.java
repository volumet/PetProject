package volumet.project.services;

import volumet.project.dto.ExternalIdentifierDto;
import volumet.project.dtos.request.PersonCreationRequestDto;

public interface UserService {
    void createUser(PersonCreationRequestDto personCreationRequestDto);

    void createUser(ExternalIdentifierDto externalIdentifierDto);
}
