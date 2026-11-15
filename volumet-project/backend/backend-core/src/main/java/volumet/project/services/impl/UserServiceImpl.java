package volumet.project.services.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import volumet.project.domains.ExternalIdentifier;
import volumet.project.domains.Person;
import volumet.project.dto.ExternalIdentifierDto;
import volumet.project.dtos.request.PersonCreationRequestDto;
import volumet.project.mappers.PersonMapper;
import volumet.project.repositories.jpa.PersonJpaRepository;
import volumet.project.services.UserService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserServiceImpl implements UserService {
    PersonJpaRepository personJpaRepository;
    PersonMapper personMapper;

    @Override
    @Transactional
    public void createUser(PersonCreationRequestDto personCreationRequestDto) {
        Person person = personMapper.toEntity(personCreationRequestDto);

        // UUID is database generated so we must flush
        personJpaRepository.saveAndFlush(person);
    }

    @Override
    @Transactional
    public void createUser(ExternalIdentifierDto externalIdentifierDto) {
        Person person = new Person();
        person.setFirstName(externalIdentifierDto.firstName());
        person.setLastName(externalIdentifierDto.lastName());

        ExternalIdentifier externalIdentifier = new ExternalIdentifier();
        externalIdentifier.setIssuer(externalIdentifierDto.issuer());
        externalIdentifier.setSubject(externalIdentifierDto.subject());

        person.addIdentifier(externalIdentifier);

        // UUID is database generated so we must flush
        personJpaRepository.saveAndFlush(person);
    }
}
