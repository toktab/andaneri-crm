package ge.andaneri.crm.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuickNoteRepository extends JpaRepository<QuickNote, Long> {

    /** Open notes first, then newest first. */
    @Query("select n from QuickNote n left join fetch n.business where n.user.id = :userId order by n.done, n.createdAt desc")
    List<QuickNote> findForUser(Long userId);

    @Query("select n from QuickNote n join fetch n.user where n.done = false and n.remindedAt is null"
            + " and n.remindAt > :from and n.remindAt <= :to")
    List<QuickNote> findDueReminders(java.time.Instant from, java.time.Instant to);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("update QuickNote n set n.remindedAt = :at where n.id = :id and n.remindedAt is null")
    int claimReminder(Long id, java.time.Instant at);
}
