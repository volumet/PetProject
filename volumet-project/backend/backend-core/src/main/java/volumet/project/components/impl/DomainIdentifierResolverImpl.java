package volumet.project.components.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import volumet.project.components.DomainIdentifierResolver;
import volumet.project.dto.ExternalIdentifierDto;
import volumet.project.repositories.query.PersonRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DomainIdentifierResolverImpl implements DomainIdentifierResolver {
    private final PersonRepository personRepository;

    @Override
    @Cacheable(cacheNames = "personUuid")
    public UUID resolveIdentifierUuid(ExternalIdentifierDto externalIdentifier) {
        return personRepository.findPersonUuidByExternalIdentifier(externalIdentifier.getIssuer(), externalIdentifier.getSubject());
    }
}
