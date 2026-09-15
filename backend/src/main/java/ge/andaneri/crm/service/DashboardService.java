package ge.andaneri.crm.service;

import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.Activity;
import ge.andaneri.crm.domain.ActivityRepository;
import ge.andaneri.crm.domain.ActivityType;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.Interest;
import ge.andaneri.crm.domain.InterestRepository;
import ge.andaneri.crm.domain.InterestStatus;
import ge.andaneri.crm.domain.Purchase;
import ge.andaneri.crm.domain.PurchaseRepository;
import ge.andaneri.crm.domain.TaskRepository;
import ge.andaneri.crm.domain.TaskStatus;
import ge.andaneri.crm.web.WorkDtos.PurchaseDto;
import ge.andaneri.crm.web.WorkDtos.TaskDto;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "I open the website at 12:00 and immediately know who to call, who to visit and what meetings
 * I have." Everything here is worked out live from tasks, purchases and contact dates, so there
 * is no reminder table to fall out of step with the data.
 */
@Service
public class DashboardService {

    /** Stages where a business is still being worked, and so can go cold. */
    static final Set<BusinessStatus> ACTIVE = EnumSet.of(BusinessStatus.NEW, BusinessStatus.CONTACTED,
            BusinessStatus.INTERESTED, BusinessStatus.MEETING, BusinessStatus.TESTING, BusinessStatus.NEGOTIATION,
            BusinessStatus.FOLLOW_UP_LATER);

    public record Alert(String kind, Long businessId, String businessName, Long days, LocalDate date,
            String detailKa, String detailEn) {
    }

    public record Stats(long callsToday, long visitsToday, long meetingsToday, long activitiesThisWeek,
            long newLeadsThisWeek, BigDecimal salesThisMonth, long purchasesThisMonth) {
    }

    public record Dashboard(List<TaskDto> overdue, List<TaskDto> today, List<TaskDto> upcoming,
            Map<BusinessStatus, Long> pipeline, List<Alert> alerts, List<PurchaseDto> recentPurchases, Stats stats,
            int staleDays, int upcomingDays) {
    }

    private final TaskRepository tasks;
    private final BusinessRepository businesses;
    private final PurchaseRepository purchases;
    private final InterestRepository interests;
    private final ActivityRepository activities;
    private final SettingsService settings;
    private final CrmProperties properties;

    public DashboardService(TaskRepository tasks, BusinessRepository businesses, PurchaseRepository purchases,
            InterestRepository interests, ActivityRepository activities, SettingsService settings, CrmProperties properties) {
        this.tasks = tasks;
        this.businesses = businesses;
        this.purchases = purchases;
        this.interests = interests;
        this.activities = activities;
        this.settings = settings;
        this.properties = properties;
    }

    /** @param userId one person's dashboard, or null for the whole team */
    @Transactional(readOnly = true)
    public Dashboard build(Long userId) {
        ZoneId zone = properties.zoneId();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(zone);
        Instant startOfToday = today.atStartOfDay(zone).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant();
        int upcomingDays = settings.getInt(SettingsService.UPCOMING_DAYS);
        int staleDays = settings.getInt(SettingsService.STALE_DAYS);
        Instant upcomingEnd = today.plusDays(1L + upcomingDays).atStartOfDay(zone).toInstant();

        List<TaskDto> overdue = tasks.findWithStatusBefore(TaskStatus.OPEN, startOfToday, userId).stream().map(TaskDto::of).toList();
        List<TaskDto> todayTasks = tasks.findInRange(startOfToday, startOfTomorrow, EnumSet.of(TaskStatus.OPEN, TaskStatus.DONE), userId)
                .stream().map(TaskDto::of).toList();
        List<TaskDto> upcoming = tasks.findInRange(startOfTomorrow, upcomingEnd, EnumSet.of(TaskStatus.OPEN), userId)
                .stream().map(TaskDto::of).toList();

        Map<BusinessStatus, Long> pipeline = new EnumMap<>(BusinessStatus.class);
        for (BusinessStatus status : BusinessStatus.values()) {
            pipeline.put(status, 0L);
        }
        for (Object[] row : businesses.countByStatus(userId)) {
            pipeline.put((BusinessStatus) row[0], (Long) row[1]);
        }

        Set<Long> withOpenTask = tasks.findBusinessIdsWithStatus(TaskStatus.OPEN);
        List<Alert> alerts = new ArrayList<>();
        alerts.addAll(reorderAlerts(userId, today, withOpenTask));
        alerts.addAll(sampleAlerts(userId));
        alerts.addAll(staleAlerts(userId, now, staleDays, withOpenTask));

        List<PurchaseDto> recent = purchases.findRecent(userId, PageRequest.of(0, 5)).stream()
                .map(p -> PurchaseDto.of(p, false)).toList();

        return new Dashboard(overdue, todayTasks, upcoming, pipeline, alerts, recent,
                stats(userId, today, zone, startOfToday, now), staleDays, upcomingDays);
    }

    /** Customers whose usual time between orders has passed and nobody has a task open for them. */
    private List<Alert> reorderAlerts(Long userId, LocalDate today, Set<Long> withOpenTask) {
        List<Business> customers = businesses.findCustomers(userId);
        if (customers.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LocalDate>> dates = new HashMap<>();
        for (Object[] row : purchases.findDates(customers.stream().map(Business::getId).toList())) {
            dates.computeIfAbsent((Long) row[0], key -> new ArrayList<>()).add((LocalDate) row[1]);
        }
        int teamDefault = settings.getInt(SettingsService.REORDER_DAYS);
        List<Alert> alerts = new ArrayList<>();
        for (Business b : customers) {
            if (withOpenTask.contains(b.getId()) || b.getStatus() == BusinessStatus.LOST) {
                continue;
            }
            List<LocalDate> history = dates.getOrDefault(b.getId(), List.of());
            int expected = Reorder.expectedDays(b.getReorderDays(), Reorder.averageGap(history), teamDefault);
            LocalDate due = b.getLastPurchaseDate().plusDays(expected);
            if (!due.isAfter(today)) {
                long since = ChronoUnit.DAYS.between(b.getLastPurchaseDate(), today);
                alerts.add(new Alert("REORDER", b.getId(), b.getName(), since, b.getLastPurchaseDate(),
                        "ჩვეულებრივ ყოველ " + expected + " დღეში", "usually every " + expected + " days"));
            }
        }
        alerts.sort(Comparator.comparing(Alert::days).reversed());
        return alerts;
    }

    private List<Alert> sampleAlerts(Long userId) {
        List<Alert> alerts = new ArrayList<>();
        Map<Long, Alert> byBusiness = new HashMap<>();
        for (Interest interest : interests.findWithStatus(InterestStatus.SAMPLE_REQUESTED, userId)) {
            String ka = interest.getProduct() != null ? interest.getProduct().getNameKa()
                    : interest.getFlavor() != null ? interest.getFlavor().getNameKa() : "";
            String en = interest.getProduct() != null ? interest.getProduct().getNameEn()
                    : interest.getFlavor() != null ? interest.getFlavor().getNameEn() : "";
            Business b = interest.getBusiness();
            Alert existing = byBusiness.get(b.getId());
            Alert merged = existing == null
                    ? new Alert("SAMPLES", b.getId(), b.getName(), null, null, ka, en)
                    : new Alert("SAMPLES", b.getId(), b.getName(), null, null, existing.detailKa() + ", " + ka, existing.detailEn() + ", " + en);
            byBusiness.put(b.getId(), merged);
        }
        alerts.addAll(byBusiness.values());
        return alerts;
    }

    /** Leads nobody has called or visited for a while, and with nothing planned. The oldest twenty. */
    private List<Alert> staleAlerts(Long userId, Instant now, int staleDays, Set<Long> withOpenTask) {
        Instant before = now.minus(staleDays, ChronoUnit.DAYS);
        List<Alert> alerts = new ArrayList<>();
        for (Business b : businesses.findStale(ACTIVE, before, userId, PageRequest.of(0, 60))) {
            if (withOpenTask.contains(b.getId())) {
                continue;
            }
            Instant since = b.getLastContactAt() != null ? b.getLastContactAt() : b.getCreatedAt();
            alerts.add(new Alert(b.getLastContactAt() == null ? "NEVER_CONTACTED" : "STALE", b.getId(), b.getName(),
                    ChronoUnit.DAYS.between(since, now), null, null, null));
            if (alerts.size() == 20) {
                break;
            }
        }
        return alerts;
    }

    private Stats stats(Long userId, LocalDate today, ZoneId zone, Instant startOfToday, Instant now) {
        Instant startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant();
        List<Activity> week = activities.findInRange(startOfWeek, now.plusSeconds(1)).stream()
                .filter(a -> userId == null || a.getUser().getId().equals(userId))
                .toList();
        List<Activity> todays = week.stream().filter(a -> !a.getOccurredAt().isBefore(startOfToday)).toList();
        long newLeads = businesses.findCreatedInRange(startOfWeek, now.plusSeconds(1)).stream()
                .filter(b -> !b.isArchived())
                .filter(b -> userId == null || b.getCreatedBy().getId().equals(userId))
                .count();
        List<Purchase> month = purchases.findInRange(today.withDayOfMonth(1), today).stream()
                .filter(p -> userId == null || p.getUser().getId().equals(userId))
                .toList();
        return new Stats(
                todays.stream().filter(a -> a.getType() == ActivityType.CALL).count(),
                todays.stream().filter(a -> a.getType() == ActivityType.VISIT).count(),
                todays.stream().filter(a -> a.getType() == ActivityType.MEETING).count(),
                week.size(),
                newLeads,
                month.stream().map(Purchase::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                month.size());
    }
}
