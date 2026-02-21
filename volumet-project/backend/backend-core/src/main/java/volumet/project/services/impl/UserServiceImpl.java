package volumet.project.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import volumet.project.domains.ExternalIdentifier;
import volumet.project.domains.Person;
import volumet.project.dto.ExternalIdentifierDto;
import volumet.project.dtos.request.PersonCreationRequestDto;
import volumet.project.mappers.PersonMapper;
import volumet.project.repositories.jpa.PersonJpaRepository;
import volumet.project.services.UserService;

@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final PersonJpaRepository personJpaRepository;
    private final PersonMapper personMapper;

    @Override
    public void createUser(PersonCreationRequestDto personCreationRequestDto) {
        Person person = personMapper.toEntity(personCreationRequestDto);

        // UUID is database generated so we must flush
        personJpaRepository.saveAndFlush(person);
    }

    @Override
    public void createUser(ExternalIdentifierDto externalIdentifierDto) {
        Person person = new Person();
        person.setFirstName(externalIdentifierDto.getFirstName());
        person.setLastName(externalIdentifierDto.getLastName());

        ExternalIdentifier externalIdentifier = new ExternalIdentifier();
        externalIdentifier.setIssuer(externalIdentifierDto.getIssuer());
        externalIdentifier.setSubject(externalIdentifierDto.getSubject());

        person.addIdentifier(externalIdentifier);

        // UUID is database generated so we must flush
        personJpaRepository.saveAndFlush(person);
    }
}
