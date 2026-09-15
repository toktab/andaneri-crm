package ge.andaneri.crm.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StatusChangeRepository extends JpaRepository<StatusChange, Long> {

    @Query("select s from StatusChange s join fetch s.user where s.business.id = :businessId order by s.changedAt desc")
    List<StatusChange> findForBusiness(Long businessId);

    @Query("select s from StatusChange s join fetch s.user join fetch s.business where s.changedAt >= :from and s.changedAt < :to")
    List<StatusChange> findInRange(Instant from, Instant to);
}
