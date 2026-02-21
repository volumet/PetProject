package volumet.project.domains;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import volumet.project.annotations.Phone;
import volumet.project.entities.AbstractAuditableEntity;

import java.time.LocalDate;

@Entity
@Table(name = "CIVIL_PROFILE")
@Getter
@Setter
public class CivilProfile extends AbstractAuditableEntity {
    @Column(name = "SOCIAL_ID", unique = true)
    @NotNull
    private String socialId;

    @Column(name = "DATE_OF_BIRTH", nullable = false)
    @NotNull
    private LocalDate dateOfBirth;

    @Email
    @Column(name = "EMAIL")
    private String email;

    @Phone
    @Column(name = "PHONE_NUMBER")
    private String phoneNumber;

    @OneToOne(mappedBy = "civilProfile")
    private Person person;
}
