package volumet.project.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PersistentConstant {
    public static final String JPA_ENTITY_NAME = "jpa_entity_name";
    public static final String SEQUENCE_SUFFIX = "_SEQUENCE";

    public static final String HIBERNATE_ID_DB_STRUCTURE_NAMING_STRATEGY = "hibernate.id.db_structure_naming_strategy";
    public static final String HIBERNATE_ID_OPTIMIZER_POOLED_PREFERRED = "hibernate.id.optimizer.pooled.preferred";
    public static final String HIBERNATE_ID_OPTIMIZER_INCREMENT_SIZE = "hibernate.id.optimizer.increment_size";
}
