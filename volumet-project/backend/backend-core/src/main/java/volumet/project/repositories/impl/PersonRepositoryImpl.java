package volumet.project.repositories.impl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import volumet.project.domains.QExternalIdentifier;
import volumet.project.domains.QPerson;
import volumet.project.repositories.query.PersonRepository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PersonRepositoryImpl implements PersonRepository {
    private final JPAQueryFactory queryFactory;

    @Override
    public UUID findPersonUuidByExternalIdentifier(String issuer, String subject) {
        QPerson person = QPerson.person;
        QExternalIdentifier externalIdentifier = QExternalIdentifier.externalIdentifier;

        return queryFactory
                .select(person.uuid)
                .from(person)
                .join(person.externalIdentifiers, externalIdentifier)
                .where(externalIdentifier.issuer.eq(issuer).and(externalIdentifier.subject.eq(subject)))
                .fetchFirst();
    }
}