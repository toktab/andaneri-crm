package ge.andaneri.crm.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface BusinessRepository extends JpaRepository<Business, Long>, JpaSpecificationExecutor<Business> {

    List<Business> findByArchivedFalse();

    @org.springframework.data.jpa.repository.Modifying
    @Query("update Business b set b.sheet = null where b.sheet.id = :sheetId")
    int clearSheet(Long sheetId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update Business b set b.sheet = :target where b.sheet.id = :sourceId")
    int moveSheet(Long sourceId, BusinessSheet target);

    @Query("select count(b) from Business b where b.archived = false and b.sheet is null")
    long countUnfiled();

    @Query("select count(b) from Business b where b.archived = false")
    long countActive();

    @Query("select b.status, count(b) from Business b where b.archived = false and (:userId is null or b.assignedTo.id = :userId) group by b.status")
    List<Object[]> countByStatus(Long userId);

    @Query("select b from Business b left join fetch b.assignedTo where b.archived = false and b.lastPurchaseDate is not null"
            + " and (:userId is null or b.assignedTo.id = :userId)")
    List<Business> findCustomers(Long userId);

    /** Businesses still being worked that nobody has talked to since {@code before}. */
    @Query("select b from Business b left join fetch b.assignedTo where b.archived = false and b.status in :statuses"
            + " and (b.lastContactAt is null or b.lastContactAt < :before) and b.createdAt < :before"
            + " and (:userId is null or b.assignedTo.id = :userId) order by b.lastContactAt")
    List<Business> findStale(Collection<BusinessStatus> statuses, Instant before, Long userId, Pageable pageable);

    @Query("select distinct b.district from Business b where b.district is not null and b.district <> '' order by b.district")
    List<String> findDistricts();

    @Query("select distinct b.city from Business b where b.city is not null and b.city <> '' order by b.city")
    List<String> findCities();

    @Query("select b from Business b left join fetch b.assignedTo where b.createdAt >= :from and b.createdAt < :to")
    List<Business> findCreatedInRange(Instant from, Instant to);
}
