package volumet.project.domains;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import volumet.project.entities.AbstractAuditableEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "STUDENT")
@Getter
@Setter
public class Student extends AbstractAuditableEntity {
    @Column(name = "STUDENT_CODE")
    @NotNull
    private String studentCode;

    @Column(name = "ENROLLMENT_DATE")
    @NotNull
    private LocalDate enrollmentDate;

    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Registration> registrations = new ArrayList<>();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "PERSON_ID", nullable = false)
    @NotNull
    private Person person;
}