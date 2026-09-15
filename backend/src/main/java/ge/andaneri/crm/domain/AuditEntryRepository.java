package ge.andaneri.crm.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    @Query("select a from AuditEntry a left join fetch a.user where a.businessId = :businessId order by a.createdAt desc")
    List<AuditEntry> findForBusiness(Long businessId);

    @Query("select a from AuditEntry a left join fetch a.user order by a.createdAt desc")
    List<AuditEntry> findRecent(Pageable pageable);
}
