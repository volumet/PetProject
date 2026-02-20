package volumet.project.domains;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import volumet.project.entities.AbstractAuditableEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ROOM")
@Getter
@Setter
public class Room extends AbstractAuditableEntity {
    @Column(name = "SIZE")
    @NotNull
    private Integer size;

    @Column(name = "ROOM_NAME")
    @NotNull
    private String roomName;

    @OneToMany(mappedBy = "room", cascade = {CascadeType.MERGE,CascadeType.REFRESH, CascadeType.REMOVE}, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Registration> registrations = new ArrayList<>();
}
