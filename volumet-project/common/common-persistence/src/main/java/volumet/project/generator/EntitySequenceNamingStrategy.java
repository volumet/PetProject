package volumet.project.generator;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.QualifiedName;
import org.hibernate.boot.model.relational.QualifiedNameParser;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.id.enhanced.ImplicitDatabaseObjectNamingStrategy;
import org.hibernate.service.ServiceRegistry;
import volumet.project.utils.StringUtils;

import java.util.Map;
import java.util.Objects;

import static volumet.project.constants.PersistentConstant.JPA_ENTITY_NAME;
import static volumet.project.constants.PersistentConstant.SEQUENCE_SUFFIX;

/**
 * Hibernate SPI implementation loaded via
 * 'hibernate.id.db_structure_naming_strategy'. <br/>
 * Entity naming must be identical to the table it represents. <br/>
 * Example: entity for <b>PERSON</b> table must be <b>Person</b>. <br/>
 * Sequence for PERSON will and must be translated as PERSON_SEQUENCE. <br/>
 *
 * It is preferred to use ImplicitDatabaseObjectNamingStrategy
 * over SequenceStyleGenerator to avoid overriding properties value.
 */
@SuppressWarnings("unused")
public class EntitySequenceNamingStrategy implements ImplicitDatabaseObjectNamingStrategy {
    @Override
    public QualifiedName determineSequenceName(Identifier catalogName, Identifier schemaName, Map<?, ?> configValues, ServiceRegistry serviceRegistry) {
        String entityName = (String) configValues.get(JPA_ENTITY_NAME);

        if (entityName == null) {
            throw new IllegalStateException("Can't determine entity name");
        }

        String sequenceName = StringUtils.toUpperSnakeCase(entityName) + SEQUENCE_SUFFIX;
        JdbcEnvironment jdbcEnvironment = Objects.requireNonNull(serviceRegistry.getService(JdbcEnvironment.class), "Jdbc environment not available");

        Identifier sequenceIdentifier = jdbcEnvironment.getIdentifierHelper().toIdentifier(sequenceName);

        return new QualifiedNameParser.NameParts(
                catalogName,
                schemaName,
                sequenceIdentifier
        );
    }

    @Override
    public QualifiedName determineTableName(Identifier identifier, Identifier identifier1, Map<?, ?> map, ServiceRegistry serviceRegistry) {
        // Table strategy is not used
        return null;
    }
}
