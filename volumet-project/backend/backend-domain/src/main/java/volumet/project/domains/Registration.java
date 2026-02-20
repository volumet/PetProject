package volumet.project.domains;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import volumet.project.entities.AbstractAuditableEntity;
import volumet.project.utils.DateUtils;

import java.time.LocalDate;

@Entity
@Table(name = "REGISTRATION")
@Getter
@Setter
public class Registration extends AbstractAuditableEntity {
    @Column(name = "ISSUED_DATE")
    @NotNull
    private LocalDate issuedDate;

    @Column(name = "EXPIRED_DATE")
    @NotNull
    private LocalDate expiredDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ROOM_ID", nullable = false)
    @NotNull
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "STUDENT_ID", nullable = false)
    @NotNull
    private Student student;

    @PrePersist
    @PreUpdate
    private void validation() {
        if (issuedDate == null || expiredDate == null) {
            return;
        }

        if (!DateUtils.isBeforeOrEquals(issuedDate, expiredDate)) {
            throw new IllegalStateException("Issued date must be before or equals to to expired date!");
        }
    }
}
