package volumet.project.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Abstract entity providing auditable values.
 */
@MappedSuperclass
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class AbstractAuditableEntity extends AbstractEntity {
    @CreatedBy
    @NotNull
    @Column(name = "USR_LOG_I", updatable = false)
    private String createdBy;

    @CreatedDate
    @NotNull
    @Column(name = "DTE_LOG_I", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedBy
    @Column(name = "USR_LOG_U")
    private String lastModifiedBy;

    @LastModifiedDate
    @Column(name = "DTE_LOG_U")
    private LocalDateTime lastModifiedDate;

    @Version
    @NotNull
    @Column(name = "VERSION")
    private Long version;
}
