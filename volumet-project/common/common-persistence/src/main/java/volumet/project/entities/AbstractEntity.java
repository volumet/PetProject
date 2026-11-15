package volumet.project.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import volumet.project.enums.EntityStatus;

/**
 * Abstract entity with default sequence id generation.
 */
@MappedSuperclass
@Getter
@Setter
public class AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(updatable = false, nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    @NotNull
    private EntityStatus status = EntityStatus.ACTIVE;
}
