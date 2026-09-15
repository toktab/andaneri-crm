package ge.andaneri.crm.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @Query("select distinct p from Purchase p join fetch p.user left join fetch p.items where p.business.id = :businessId")
    List<Purchase> findForBusiness(Long businessId);

    @Query("select distinct p from Purchase p join fetch p.business b left join fetch b.type join fetch p.user left join fetch p.items"
            + " where p.purchaseDate >= :from and p.purchaseDate <= :to")
    List<Purchase> findInRange(LocalDate from, LocalDate to);

    @Query("select p from Purchase p join fetch p.business b join fetch p.user where b.archived = false"
            + " and (:userId is null or b.assignedTo.id = :userId) order by p.purchaseDate desc, p.id desc")
    List<Purchase> findRecent(Long userId, Pageable pageable);

    /** (business id, purchase date) pairs, oldest first, for working out how often each customer orders. */
    @Query("select p.business.id, p.purchaseDate from Purchase p where p.business.id in :ids order by p.purchaseDate")
    List<Object[]> findDates(Collection<Long> ids);
}
