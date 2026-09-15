package ge.andaneri.crm.notify;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.Task;
import ge.andaneri.crm.domain.TaskRepository;
import ge.andaneri.crm.domain.TaskStatus;
import ge.andaneri.crm.domain.TaskType;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tasks in the phone's own calendar: a personal feed address to subscribe to (it keeps itself up to date),
 * and a one-task file for "Add to calendar". Both are opened by the calendar app, which cannot send the
 * sign-in token, so they are addressed with the person's secret calendar token instead.
 */
@RestController
public class CalendarController {

    public record CalendarInfo(String token) {
    }

    public record CalendarLink(String path) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    private final UserRepository users;
    private final TaskRepository tasks;
    private final CurrentUser currentUser;
    private final CrmProperties properties;

    public CalendarController(UserRepository users, TaskRepository tasks, CurrentUser currentUser, CrmProperties properties) {
        this.users = users;
        this.tasks = tasks;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    // ---------------------------------------------------------------- managing the feed (signed in)

    @GetMapping("/api/me/calendar")
    public CalendarInfo info() {
        return new CalendarInfo(currentUser.require().getCalendarToken());
    }

    /** Turns the feed on, or gives it a new address: whoever had the old one stops seeing the calendar. */
    @PostMapping("/api/me/calendar")
    @Transactional
    public CalendarInfo regenerate() {
        User user = users.findById(currentUser.require().getId()).orElseThrow(ApiException::notFound);
        user.setCalendarToken(newToken());
        return new CalendarInfo(user.getCalendarToken());
    }

    @DeleteMapping("/api/me/calendar")
    @Transactional
    public CalendarInfo turnOff() {
        User user = users.findById(currentUser.require().getId()).orElseThrow(ApiException::notFound);
        user.setCalendarToken(null);
        return new CalendarInfo(null);
    }

    /** An address the calendar app can open for one task. Creates the person's token if they had none. */
    @PostMapping("/api/tasks/{id}/calendar-link")
    @Transactional
    public CalendarLink link(@PathVariable Long id, @RequestParam(defaultValue = "ka") String lang) {
        User user = users.findById(currentUser.require().getId()).orElseThrow(ApiException::notFound);
        tasks.findById(id).orElseThrow(ApiException::notFound);
        if (user.getCalendarToken() == null) {
            user.setCalendarToken(newToken());
        }
        return new CalendarLink("/cal/" + user.getCalendarToken() + "/tasks/" + id + ".ics" + ("en".equals(lang) ? "?lang=en" : ""));
    }

    // ---------------------------------------------------------------- read by calendar apps (token, no sign-in)

    @GetMapping("/cal/{token}.ics")
    @Transactional(readOnly = true)
    public ResponseEntity<String> feed(@PathVariable String token, @RequestParam(defaultValue = "ka") String lang, HttpServletRequest request) {
        User user = byToken(token);
        Instant now = Instant.now();
        List<IcsWriter.Event> events = new ArrayList<>();
        for (Task task : tasks.findInRange(now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(120)),
                EnumSet.of(TaskStatus.OPEN, TaskStatus.DONE), user.getId())) {
            events.add(event(task, user, lang, baseUrl(request)));
        }
        String name = "en".equals(lang) ? "Andaneri CRM - " + user.getFullName() : "Andaneri CRM - " + user.getFullName();
        return ResponseEntity.ok().contentType(CALENDAR).cacheControl(CacheControl.noCache())
                .body(IcsWriter.calendar(name, events, now));
    }

    @GetMapping("/cal/{token}/tasks/{id}.ics")
    @Transactional(readOnly = true)
    public ResponseEntity<String> single(@PathVariable String token, @PathVariable Long id, @RequestParam(defaultValue = "ka") String lang,
            HttpServletRequest request) {
        User user = byToken(token);
        Task task = tasks.findById(id).orElseThrow(ApiException::notFound);
        String body = IcsWriter.calendar("Andaneri CRM", List.of(event(task, user, lang, baseUrl(request))), Instant.now());
        return ResponseEntity.ok().contentType(CALENDAR)
                .header("Content-Disposition", "inline; filename=\"andaneri-" + id + ".ics\"")
                .body(body);
    }

    private User byToken(String token) {
        if (token == null || token.length() < 20) {
            throw ApiException.notFound();
        }
        return users.findByCalendarToken(token).filter(User::isActive).orElseThrow(ApiException::notFound);
    }

    private IcsWriter.Event event(Task task, User viewer, String lang, String baseUrl) {
        boolean en = "en".equals(lang);
        ZoneId zone = properties.zoneId();
        String who = task.getBusiness() != null ? task.getBusiness().getName() : task.getTitle();
        String summary = (task.getStatus() == TaskStatus.DONE ? "✓ " : "") + ReminderText.typeLabel(task.getType(), en)
                + (who == null ? "" : ": " + who);

        List<String> details = new ArrayList<>();
        if (task.getBusiness() != null && task.getTitle() != null) {
            details.add(task.getTitle());
        }
        if (task.getContact() != null) {
            details.add(task.getContact().getName() + (task.getContact().getPhone() == null ? "" : " " + task.getContact().getPhone()));
        }
        if (task.getBusiness() != null && task.getBusiness().getPhone() != null) {
            details.add((en ? "Phone: " : "ტელ.: ") + task.getBusiness().getPhone());
        }
        if (task.getNotes() != null) {
            details.add(task.getNotes());
        }
        String url = task.getBusiness() != null ? baseUrl + "/main?open=" + task.getBusiness().getId() : baseUrl + "/tasks";
        details.add(url);

        String location = task.getLocation() != null ? task.getLocation() : task.getBusiness() != null ? task.getBusiness().getAddress() : null;
        boolean longer = task.getType() == TaskType.MEETING || task.getType() == TaskType.VISIT;
        Instant end = task.getEndAt() != null ? task.getEndAt() : task.getDueAt().plus(Duration.ofMinutes(longer ? 60 : 30));
        Integer alarm = null;
        if (task.getStatus() == TaskStatus.OPEN) {
            int minutes = task.getRemindMinutes() != null ? task.getRemindMinutes()
                    : task.getAssignedTo() != null ? task.getAssignedTo().getReminderMinutes() : viewer.getReminderMinutes();
            alarm = minutes > 0 ? minutes : null;
        }
        return new IcsWriter.Event("task-" + task.getId() + "@andaneri-crm", task.getDueAt(), end,
                task.isAllDay() ? task.getDueAt().atZone(zone).toLocalDate() : null, summary, location,
                String.join("\n", details), url, alarm);
    }

    /** The CRM's own address as the visitor sees it (behind Railway's proxy: https and the public host). */
    private static String baseUrl(HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) {
            host = request.getHeader("Host");
        }
        String scheme = proto != null ? proto.split(",")[0].trim() : request.getScheme();
        return scheme + "://" + (host != null ? host.split(",")[0].trim() : request.getServerName() + ":" + request.getServerPort());
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
