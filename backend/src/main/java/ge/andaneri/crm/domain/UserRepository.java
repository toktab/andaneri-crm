package ge.andaneri.crm.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    List<User> findAllByOrderByFullNameAsc();

    Optional<User> findByCalendarToken(String calendarToken);
}
