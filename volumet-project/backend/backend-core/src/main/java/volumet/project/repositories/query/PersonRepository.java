package volumet.project.repositories.query;

import java.util.UUID;

public interface PersonRepository {
    UUID findPersonUuidByExternalIdentifier(String sub, String issuer);
}
