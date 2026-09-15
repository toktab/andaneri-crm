package ge.andaneri.crm.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryUsageRepository extends JpaRepository<CategoryUsage, Long> {

    List<CategoryUsage> findByBusinessId(Long businessId);

    Optional<CategoryUsage> findByBusinessIdAndCategoryId(Long businessId, Long categoryId);
}
