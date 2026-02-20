package volumet.project.domains;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import volumet.project.annotations.Phone;
import volumet.project.entities.AbstractAuditableEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "PERSON")
@Getter
@Setter
public class Person extends AbstractAuditableEntity {

    @Column(name = "SOCIAL_ID")
    @NotNull
    private String socialId;

    @Column(name = "UUID", nullable = false, unique = true, insertable = false, updatable = false)
    private UUID uuid;

    @Column(name = "FIRST_NAME", nullable = false)
    @NotNull
    private String firstName;

    @Column(name = "LAST_NAME", nullable = false)
    @NotNull
    private String lastName;

    @Column(name = "DATE_OF_BIRTH", nullable = false)
    @NotNull
    private LocalDate dateOfBirth;

    @Email
    @Column(name = "EMAIL")
    private String email;

    @Phone
    @Column(name = "PHONE_NUMBER")
    private String phoneNumber;

    @OneToMany(mappedBy = "person", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Student> students = new ArrayList<>();

    @OneToMany(mappedBy = "person", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Size(min = 1)
    private List<ExternalIdentifier> externalIdentifiers = new ArrayList<>();

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
