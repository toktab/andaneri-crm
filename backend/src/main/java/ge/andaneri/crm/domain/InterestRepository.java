package ge.andaneri.crm.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InterestRepository extends JpaRepository<Interest, Long> {

    @Query("select i from Interest i left join fetch i.flavor left join fetch i.product where i.business.id in :ids order by i.id")
    List<Interest> findForBusinesses(Collection<Long> ids);

    /** Interests recorded or changed in a period, with the product's flavors for the flavor report. */
    @Query("select distinct i from Interest i join fetch i.business join fetch i.createdBy left join fetch i.flavor"
            + " left join fetch i.product p left join fetch p.flavors where i.updatedAt >= :from and i.updatedAt < :to")
    List<Interest> findUpdatedInRange(java.time.Instant from, java.time.Instant to);

    @Query("select i from Interest i left join fetch i.flavor left join fetch i.product where i.business.id = :businessId order by i.id")
    List<Interest> findForBusiness(Long businessId);

    @Query("select i from Interest i join fetch i.business b left join fetch i.flavor left join fetch i.product"
            + " where i.status = :status and b.archived = false and (:userId is null or b.assignedTo.id = :userId) order by i.updatedAt desc")
    List<Interest> findWithStatus(InterestStatus status, Long userId);
}
