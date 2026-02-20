package volumet.project.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import volumet.project.domains.Room;

public interface RoomJpaRepository extends JpaRepository<Room, Long> {
}
