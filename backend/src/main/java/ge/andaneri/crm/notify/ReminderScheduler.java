package ge.andaneri.crm.notify;

import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.QuickNote;
import ge.andaneri.crm.domain.QuickNoteRepository;
import ge.andaneri.crm.domain.Task;
import ge.andaneri.crm.domain.TaskRepository;
import ge.andaneri.crm.domain.TaskStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Once a minute: which tasks and notes should be reminded now, and push them to their owner's devices.
 * Each reminder is claimed in the database before it is sent, so it goes out once even if two copies of
 * the server overlap for a moment during a redeploy.
 */
@Component
public class ReminderScheduler {

    /** A reminder that could not go out in time (server restarting) is still sent this long after the start. */
    static final Duration GRACE = Duration.ofMinutes(10);
    /** All-day tasks are reminded on the morning of their day. */
    static final LocalTime ALL_DAY_AT = LocalTime.of(9, 0);
    /** The longest reminder a task can ask for (a week), so the search never misses one. */
    private static final Duration LONGEST = Duration.ofDays(7).plusMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final TaskRepository tasks;
    private final QuickNoteRepository notes;
    private final PushService push;
    private final CrmProperties properties;

    public ReminderScheduler(TaskRepository tasks, QuickNoteRepository notes, PushService push, CrmProperties properties) {
        this.tasks = tasks;
        this.notes = notes;
        this.push = push;
        this.properties = properties;
    }

    @Scheduled(initialDelay = 30_000, fixedDelayString = "${crm.reminder-check-ms:60000}")
    public void tick() {
        if (!properties.remindersEnabled()) {
            return;
        }
        try {
            runOnce(Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Reminder check failed: {}", ex.toString());
        }
    }

    /** Sends everything due at {@code now}; returns how many reminders were claimed. */
    public int runOnce(Instant now) {
        ZoneId zone = properties.zoneId();
        int claimed = 0;
        for (Task task : tasks.findReminderCandidates(TaskStatus.OPEN, now.minus(Duration.ofDays(1)), now.plus(LONGEST))) {
            Instant at = remindAt(task, zone);
            if (at == null || now.isBefore(at) || !now.isBefore(windowEnd(task, at))) {
                continue;
            }
            if (tasks.claimReminder(task.getId(), now) == 1) {
                claimed++;
                push.sendToUser(task.getAssignedTo().getId(), lang -> ReminderText.task(task, lang, zone));
            }
        }
        for (QuickNote note : notes.findDueReminders(now.minus(GRACE), now)) {
            if (notes.claimReminder(note.getId(), now) == 1) {
                claimed++;
                push.sendToUser(note.getUser().getId(), lang -> ReminderText.note(note, lang));
            }
        }
        return claimed;
    }

    /** When this task's reminder is due, or null when it has none. */
    static Instant remindAt(Task task, ZoneId zone) {
        int minutes = task.getRemindMinutes() != null ? task.getRemindMinutes() : task.getAssignedTo().getReminderMinutes();
        if (minutes <= 0) {
            return null;
        }
        if (task.isAllDay()) {
            return task.getDueAt().atZone(zone).toLocalDate().atTime(ALL_DAY_AT).atZone(zone).toInstant();
        }
        return task.getDueAt().minus(Duration.ofMinutes(minutes));
    }

    private static Instant windowEnd(Task task, Instant at) {
        return task.isAllDay() ? at.plus(Duration.ofHours(12)) : task.getDueAt().plus(GRACE);
    }
}
