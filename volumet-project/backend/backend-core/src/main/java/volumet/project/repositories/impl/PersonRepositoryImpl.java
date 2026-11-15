package volumet.project.repositories.impl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Repository;
import volumet.project.domains.QExternalIdentifier;
import volumet.project.domains.QPerson;
import volumet.project.repositories.query.PersonRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PersonRepositoryImpl implements PersonRepository {
    JPAQueryFactory queryFactory;

    @Override
    public Optional<UUID> findPersonUuidByExternalIdentifier(String issuer, String subject) {
        QPerson person = QPerson.person;
        QExternalIdentifier externalIdentifier = QExternalIdentifier.externalIdentifier;

        return Optional.ofNullable(queryFactory
                .select(person.uuid)
                .from(person)
                .join(person.externalIdentifiers, externalIdentifier)
                .where(externalIdentifier.issuer.eq(issuer).and(externalIdentifier.subject.eq(subject)))
                .fetchFirst());
    }
}