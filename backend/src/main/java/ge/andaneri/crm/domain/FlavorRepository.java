package ge.andaneri.crm.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlavorRepository extends JpaRepository<Flavor, Long> {

    List<Flavor> findAllByOrderByNameEnAsc();

    Optional<Flavor> findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(String nameKa, String nameEn);
}
