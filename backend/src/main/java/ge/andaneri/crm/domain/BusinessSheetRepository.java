package ge.andaneri.crm.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BusinessSheetRepository extends JpaRepository<BusinessSheet, Long> {

    @Query("select s from BusinessSheet s left join fetch s.workbook order by s.sortOrder, s.id")
    List<BusinessSheet> findAllByOrderBySortOrderAscIdAsc();

    List<BusinessSheet> findByWorkbookIdOrderBySortOrderAscIdAsc(Long workbookId);

    Optional<BusinessSheet> findFirstByWorkbookIdAndNameIgnoreCase(Long workbookId, String name);

    Optional<BusinessSheet> findFirstByWorkbookIsNullAndNameIgnoreCase(String name);

    long countByWorkbookId(Long workbookId);

    /** (sheet id, number of businesses not archived) */
    @Query("select b.sheet.id, count(b) from Business b where b.archived = false and b.sheet is not null group by b.sheet.id")
    List<Object[]> countBusinesses();

    @Modifying
    @Query("update BusinessSheet s set s.workbook = :target where s.workbook.id = :sourceId")
    int moveAllToWorkbook(Long sourceId, Workbook target);

    @Modifying
    @Query("update BusinessSheet s set s.workbook = null where s.workbook.id = :workbookId")
    int detachFromWorkbook(Long workbookId);
}
