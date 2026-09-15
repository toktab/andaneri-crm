package ge.andaneri.crm.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("select t from Task t left join fetch t.business left join fetch t.contact join fetch t.assignedTo"
            + " where t.dueAt >= :from and t.dueAt < :to and t.status in :statuses"
            + " and (:userId is null or t.assignedTo.id = :userId) order by t.dueAt")
    List<Task> findInRange(Instant from, Instant to, Collection<TaskStatus> statuses, Long userId);

    @Query("select t from Task t left join fetch t.business left join fetch t.contact join fetch t.assignedTo"
            + " where t.status = :status and t.dueAt < :before and (:userId is null or t.assignedTo.id = :userId) order by t.dueAt")
    List<Task> findWithStatusBefore(TaskStatus status, Instant before, Long userId);

    @Query("select t from Task t left join fetch t.contact join fetch t.assignedTo left join fetch t.completedBy"
            + " where t.business.id = :businessId order by t.dueAt")
    List<Task> findForBusiness(Long businessId);

    @Query("select t from Task t where t.business.id in :ids and t.status = :status order by t.dueAt")
    List<Task> findForBusinesses(Collection<Long> ids, TaskStatus status);

    @Query("select distinct t.business.id from Task t where t.status = :status and t.business is not null")
    Set<Long> findBusinessIdsWithStatus(TaskStatus status);

    @Query("select t from Task t join fetch t.assignedTo where t.status = :status and t.completedAt >= :from and t.completedAt < :to")
    List<Task> findCompletedInRange(TaskStatus status, Instant from, Instant to);

    /** Open tasks around now whose reminder has not gone out yet; the scheduler decides which are due. */
    @Query("select t from Task t join fetch t.assignedTo left join fetch t.business left join fetch t.contact"
            + " where t.status = :status and t.remindedAt is null and t.dueAt >= :from and t.dueAt < :to")
    List<Task> findReminderCandidates(TaskStatus status, Instant from, Instant to);

    /** Marks the reminder as sent, only if nobody else did: the one that gets 1 back sends it. */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Task t set t.remindedAt = :at where t.id = :id and t.remindedAt is null")
    int claimReminder(Long id, Instant at);
}
