package volumet.project.repositories.query;

import java.util.Optional;
import java.util.UUID;

public interface PersonRepository {
    Optional<UUID> findPersonUuidByExternalIdentifier(String sub, String issuer);
}
