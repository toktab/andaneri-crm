package ge.andaneri.crm.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    @Query("select a from Activity a join fetch a.user left join fetch a.contact where a.business.id = :businessId order by a.occurredAt desc, a.id desc")
    List<Activity> findForBusiness(Long businessId);

    @Query("select a from Activity a join fetch a.business join fetch a.user where a.occurredAt >= :from and a.occurredAt < :to")
    List<Activity> findInRange(Instant from, Instant to);
}
