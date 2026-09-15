package ge.andaneri.crm.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkbookRepository extends JpaRepository<Workbook, Long> {

    List<Workbook> findAllByOrderBySortOrderAscIdAsc();

    Optional<Workbook> findFirstByNameIgnoreCase(String name);
}
