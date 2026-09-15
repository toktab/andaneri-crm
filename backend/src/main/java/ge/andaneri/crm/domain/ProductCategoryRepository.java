package ge.andaneri.crm.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findAllByOrderBySortOrderAscIdAsc();

    Optional<ProductCategory> findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(String nameKa, String nameEn);
}
