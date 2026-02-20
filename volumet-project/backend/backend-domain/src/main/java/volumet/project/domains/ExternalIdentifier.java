package volumet.project.domains;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import volumet.project.entities.AbstractAuditableEntity;

@Entity
@Table(name = "EXTERNAL_IDENTIFIER",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"ISSUER", "SUBJECT"}
        ))
@Getter
@Setter
public class ExternalIdentifier extends AbstractAuditableEntity {
    @Column(name = "ISSUER")
    @NotNull
    private String issuer;

    @Column(name = "SUBJECT")
    @NotNull
    private String subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PERSON_ID", nullable = false)
    @NotNull
    private Person person;
}