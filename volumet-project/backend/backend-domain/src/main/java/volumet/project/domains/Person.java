package volumet.project.domains;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import volumet.project.entities.AbstractAuditableEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "PERSON")
@Getter
@Setter
public class Person extends AbstractAuditableEntity {
    @Column(name = "UUID", nullable = false, unique = true, insertable = false, updatable = false)
    private UUID uuid;

    @Column(name = "FIRST_NAME", nullable = false)
    @NotNull
    private String firstName;

    @Column(name = "LAST_NAME", nullable = false)
    @NotNull
    private String lastName;

    @OneToMany(mappedBy = "person", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Student> students = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "PROFILE_ID", unique = true)
    private CivilProfile civilProfile;

    @OneToMany(mappedBy = "person", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Size(min = 1)
    private List<ExternalIdentifier> externalIdentifiers = new ArrayList<>();

    public void addCivilProfile(@NonNull CivilProfile civilProfile) {
        this.civilProfile = civilProfile;
        civilProfile.setPerson(this);
    }

    public void addIdentifier(@NonNull ExternalIdentifier identifier) {
        externalIdentifiers.add(identifier);
        identifier.setPerson(this);
    }

    public void removeIdentifier(@NonNull ExternalIdentifier identifier) {
        externalIdentifiers.remove(identifier);
        identifier.setPerson(null);

        if (externalIdentifiers.isEmpty()) {
            throw new IllegalStateException("Must contain at least 1 identifier!");
        }
    }
}
