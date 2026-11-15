package volumet.project.components;

import volumet.project.dto.ExternalIdentifierDto;

/**
 * Contract to resolve identifier after security filter.
 */
public interface DomainIdentifierResolver {
    String resolveIdentifierUuid(ExternalIdentifierDto externalIdentifier);
}