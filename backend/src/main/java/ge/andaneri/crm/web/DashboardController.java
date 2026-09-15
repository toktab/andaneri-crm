package ge.andaneri.crm.web;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.service.BusinessService;
import ge.andaneri.crm.service.DashboardService;
import ge.andaneri.crm.service.DashboardService.Dashboard;
import ge.andaneri.crm.service.ReportService;
import ge.andaneri.crm.service.ReportService.Report;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboard;
    private final ReportService reports;
    private final BusinessService businesses;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService dashboard, ReportService reports, BusinessService businesses, CurrentUser currentUser) {
        this.dashboard = dashboard;
        this.reports = reports;
        this.businesses = businesses;
        this.currentUser = currentUser;
    }

    /**
     * {@code scope=mine} (the default) is the signed-in person's day; {@code scope=team} everyone's;
     * {@code userId} one colleague's, for a supervisor checking in on someone.
     */
    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestParam(defaultValue = "mine") String scope, @RequestParam(required = false) Long userId) {
        User user = currentUser.require();
        Long who = userId != null ? userId : "team".equals(scope) ? null : user.getId();
        return dashboard.build(who);
    }

    /** Defaults to the current month so far. */
    @GetMapping("/reports")
    public Report report(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long userId) {
        currentUser.require();
        LocalDate today = businesses.today();
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        return reports.build(start, end, userId);
    }
}
