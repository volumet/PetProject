package volumet.project.components;

import volumet.project.dto.ExternalIdentifierDto;

import java.util.UUID;

/**
 * Contract to resolve identifier upon security filter
 */
public interface DomainIdentifierResolver {
    UUID resolveIdentifierUuid(ExternalIdentifierDto externalIdentifier);
}
