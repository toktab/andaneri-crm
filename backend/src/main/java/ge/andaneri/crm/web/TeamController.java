package ge.andaneri.crm.web;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.domain.ActivityRepository;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.service.PresenceService;
import ge.andaneri.crm.service.ReportService;
import ge.andaneri.crm.service.ReportService.Report;
import ge.andaneri.crm.web.UserDtos.UserRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The supervisor's window on the team: who did how much over a period, when each was last at work, and
 * who is on which place at this very moment. Reading only - nothing here changes anybody's data.
 *
 * <p>Presence is also written here: call mode says where it is every half minute, which is what lets one
 * salesperson see that a colleague is already on the phone with the same bar.
 */
@RestController
@RequestMapping("/api")
public class TeamController {

    /** One person's period: what they did, where they are, when they were last seen. */
    public record TeamRow(UserRef user, String role, boolean active, long calls, long callsReached, long visits,
            long meetings, long newLeads, long newCustomers, long purchases, java.math.BigDecimal sales, long bottles,
            long tasksDone, Instant lastActivityAt, Instant lastLoginAt, PresenceService.Where nowOn) {
    }

    public record TeamView(LocalDate from, LocalDate to, List<TeamRow> rows) {
    }

    public record HereRequest(Long businessId) {
    }

    public record Here(List<PresenceService.Where> others) {
    }

    private final ReportService reports;
    private final UserRepository users;
    private final ActivityRepository activities;
    private final BusinessRepository businesses;
    private final PresenceService presence;
    private final CurrentUser currentUser;

    public TeamController(ReportService reports, UserRepository users, ActivityRepository activities,
            BusinessRepository businesses, PresenceService presence, CurrentUser currentUser) {
        this.reports = reports;
        this.users = users;
        this.activities = activities;
        this.businesses = businesses;
        this.presence = presence;
        this.currentUser = currentUser;
    }

    /** The whole team over a period, defaulting to this month. */
    @GetMapping("/team")
    public TeamView team(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        currentUser.requireSupervisor();
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        Report report = reports.build(start, end, null);
        Map<Long, ReportService.UserRow> numbers = report.byUser().stream()
                .collect(Collectors.toMap(ReportService.UserRow::userId, Function.identity(), (a, b) -> a));

        List<TeamRow> rows = new ArrayList<>();
        for (User person : users.findAllByOrderByFullNameAsc()) {
            ReportService.UserRow row = numbers.get(person.getId());
            rows.add(new TeamRow(UserRef.of(person), person.getRole().name(), person.isActive(),
                    row == null ? 0 : row.calls(), row == null ? 0 : row.callsReached(), row == null ? 0 : row.visits(),
                    row == null ? 0 : row.meetings(), row == null ? 0 : row.newLeads(), row == null ? 0 : row.newCustomers(),
                    row == null ? 0 : row.purchases(), row == null ? java.math.BigDecimal.ZERO : row.sales(),
                    row == null ? 0 : row.bottles().longValue(), row == null ? 0 : row.tasksDone(),
                    activities.lastOccurredAt(person.getId()), person.getLastLoginAt(), presence.of(person.getId())));
        }
        return new TeamView(start, end, rows);
    }

    /** Everyone who is working somewhere right now. */
    @GetMapping("/team/now")
    public List<PresenceService.Where> now() {
        currentUser.requireSupervisor();
        return presence.live();
    }

    /**
     * "I am on this one." Sent by call mode every half minute; the answer says who else is on the same
     * place, so two people do not ring the same bar at the same time.
     */
    @PostMapping("/presence")
    public Here here(@RequestBody HereRequest request) {
        User me = currentUser.require();
        String name = request.businessId() == null ? null
                : businesses.findById(request.businessId()).map(b -> b.getName()).orElse(null);
        return new Here(presence.seen(me, request.businessId(), name));
    }

    /** Left call mode: stop showing this person there. */
    @PostMapping("/presence/leave")
    public Here leave() {
        presence.left(currentUser.require().getId());
        return new Here(List.of());
    }

    /** Who else is on this business at this moment - shown on the business file itself. */
    @GetMapping("/businesses/{id}/who-is-here")
    public Here whoIsHere(@PathVariable Long id) {
        User me = currentUser.require();
        return new Here(presence.othersOn(id, me.getId()));
    }
}
