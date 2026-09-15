package ge.andaneri.crm.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldRepository extends JpaRepository<CustomField, Long> {

    List<CustomField> findAllByOrderBySortOrderAscIdAsc();

    Optional<CustomField> findFirstByLabelIgnoreCase(String label);
}
