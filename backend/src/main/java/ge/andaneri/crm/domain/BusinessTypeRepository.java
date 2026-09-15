package ge.andaneri.crm.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessTypeRepository extends JpaRepository<BusinessType, Long> {

    List<BusinessType> findAllByOrderBySortOrderAscIdAsc();

    Optional<BusinessType> findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(String nameKa, String nameEn);
}
