package ge.andaneri.crm.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BusinessFieldValueRepository extends JpaRepository<BusinessFieldValue, Long> {

    Optional<BusinessFieldValue> findByBusinessIdAndFieldId(Long businessId, Long fieldId);

    List<BusinessFieldValue> findByBusinessId(Long businessId);

    @Query("select v from BusinessFieldValue v where v.business.id in :ids")
    List<BusinessFieldValue> findForBusinesses(Collection<Long> ids);
}
