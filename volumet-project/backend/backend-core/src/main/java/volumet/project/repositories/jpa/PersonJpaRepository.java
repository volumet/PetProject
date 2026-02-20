package volumet.project.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import volumet.project.domains.Person;

public interface PersonJpaRepository extends JpaRepository<Person, Long> {
}
