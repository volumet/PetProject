package volumet.project.components.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import volumet.project.components.DomainIdentifierResolver;
import volumet.project.dto.ExternalIdentifierDto;
import volumet.project.repositories.query.PersonRepository;
import volumet.project.services.UserService;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DomainIdentifierResolverImpl implements DomainIdentifierResolver {
    PersonRepository personRepository;
    UserService userService;

    @Override
    @Transactional
    @Cacheable(
            cacheNames = "personUuid",
            key = "#externalIdentifierDto.issuer() + ':' + #externalIdentifierDto.subject()",
            unless = "#result == null")
    public String resolveIdentifierUuid(ExternalIdentifierDto externalIdentifierDto) {
        return personRepository.findPersonUuidByExternalIdentifier(externalIdentifierDto.issuer(), externalIdentifierDto.subject())
                .map(UUID::toString)
                .orElseGet(() -> resolveSafely(externalIdentifierDto));
    }

    private String resolveSafely(ExternalIdentifierDto externalIdentifierDto) {
        try {
            userService.createUser(externalIdentifierDto);
            Optional<UUID> uuid = personRepository.findPersonUuidByExternalIdentifier(externalIdentifierDto.issuer(), externalIdentifierDto.subject());
            return uuid.map(UUID::toString).orElseThrow(() -> new IllegalStateException("Failed to resolve UUID after creation"));
        }
        catch (DataIntegrityViolationException _) {
            // User created by another request
            return personRepository.findPersonUuidByExternalIdentifier(externalIdentifierDto.issuer(), externalIdentifierDto.subject())
                    .map(UUID::toString)
                    .orElseThrow(() -> new IllegalStateException("Race condition after resolving identifier"));
        }
    }
}