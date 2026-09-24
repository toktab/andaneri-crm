package ge.andaneri.crm.service;

import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.Activity;
import ge.andaneri.crm.domain.ActivityRepository;
import ge.andaneri.crm.domain.ActivityResult;
import ge.andaneri.crm.domain.ActivityType;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.Flavor;
import ge.andaneri.crm.domain.Interest;
import ge.andaneri.crm.domain.InterestRepository;
import ge.andaneri.crm.domain.InterestStatus;
import ge.andaneri.crm.domain.ProductUsageRepository;
import ge.andaneri.crm.domain.Purchase;
import ge.andaneri.crm.domain.PurchaseItem;
import ge.andaneri.crm.domain.PurchaseRepository;
import ge.andaneri.crm.domain.StatusChange;
import ge.andaneri.crm.domain.StatusChangeRepository;
import ge.andaneri.crm.domain.Task;
import ge.andaneri.crm.domain.TaskRepository;
import ge.andaneri.crm.domain.TaskStatus;
import ge.andaneri.crm.domain.TastingFeedback;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Numbers for a period: how much work was done, how people answered, what it turned into, what
 * sold and which flavors win. Worked out in memory from the period's rows, which is plenty for a
 * sales team of a few people.
 */
@Service
public class ReportService {

    static final Set<ActivityResult> NOT_REACHED = EnumSet.of(ActivityResult.NO_ANSWER, ActivityResult.WRONG_NUMBER);
    /** Answers that move a sale forward. */
    static final Set<ActivityResult> SAID_YES = EnumSet.of(ActivityResult.INTERESTED, ActivityResult.MEETING_SET,
            ActivityResult.SAMPLES_REQUESTED, ActivityResult.ORDERED);
    static final Set<ActivityResult> SAID_NO = EnumSet.of(ActivityResult.NOT_INTERESTED);
    private static final Set<BusinessStatus> CUSTOMER = EnumSet.of(BusinessStatus.CUSTOMER, BusinessStatus.REPEAT_CUSTOMER);
    private static final Set<InterestStatus> WANTED = EnumSet.of(InterestStatus.INTERESTED, InterestStatus.VERY_INTERESTED,
            InterestStatus.SAMPLE_REQUESTED, InterestStatus.TESTING, InterestStatus.PURCHASED);

    /**
     * How calls, meetings and visits went. "Answered" is every call that reached someone; of those,
     * "said yes" moved forward, "said no" refused, and "talked" is everything in between (call back later...).
     */
    public record Funnel(long calls, long callsNoAnswer, long callsAnswered, long callsSaidYes, long callsSaidNo,
            long callsTalked, long meetings, long meetingsSaidYes, long meetingsSaidNo, long visits, long visitsSaidYes,
            long visitsSaidNo, long samplesSent, long businessesCalled, long businessesReached, long businessesMet,
            long becameClients, long bottlesSold) {
    }

    public record UserRow(Long userId, String name, long calls, long callsReached, long visits, long meetings,
            long samples, long newLeads, long newCustomers, long purchases, BigDecimal sales, BigDecimal bottles,
            long tasksDone) {
    }

    public record ProductRow(Long productId, String nameKa, String nameEn, BigDecimal quantity, BigDecimal total) {
    }

    /** {@code count} is how many businesses (or interests); {@code quantity} is bottles, where that applies. */
    public record FlavorRow(Long flavorId, String nameKa, String nameEn, long count, BigDecimal quantity) {
    }

    public record BrandRow(String name, boolean own, long businesses) {
    }

    public record GroupRow(String key, long purchases, BigDecimal total) {
    }

    public record Report(LocalDate from, LocalDate to, long newLeads, long calls, long callsReached, long visits,
            long meetings, long samples, long newCustomers, long lost, long purchases, BigDecimal salesTotal,
            long tasksDone, long overdueNow, long contactedBusinesses, Double conversionPercent, Funnel funnel,
            BigDecimal bottlesSold, List<UserRow> byUser, List<ProductRow> byProduct, List<FlavorRow> flavorsSold,
            List<FlavorRow> flavorsWanted, List<FlavorRow> flavorsLiked, List<FlavorRow> flavorsDisliked,
            List<BrandRow> marketBrands, List<FlavorRow> marketFlavors, List<GroupRow> byType, List<GroupRow> byDistrict,
            Map<BusinessStatus, Long> pipeline) {
    }

    private final ActivityRepository activities;
    private final BusinessRepository businesses;
    private final PurchaseRepository purchases;
    private final StatusChangeRepository statusChanges;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final InterestRepository interests;
    private final ProductUsageRepository usages;
    private final CrmProperties properties;

    public ReportService(ActivityRepository activities, BusinessRepository businesses, PurchaseRepository purchases,
            StatusChangeRepository statusChanges, TaskRepository tasks, UserRepository users, InterestRepository interests,
            ProductUsageRepository usages, CrmProperties properties) {
        this.activities = activities;
        this.businesses = businesses;
        this.purchases = purchases;
        this.statusChanges = statusChanges;
        this.tasks = tasks;
        this.users = users;
        this.interests = interests;
        this.usages = usages;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Report build(LocalDate from, LocalDate to, Long userId) {
        ZoneId zone = properties.zoneId();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        List<Activity> acts = activities.findInRange(start, end).stream()
                .filter(a -> userId == null || a.getUser().getId().equals(userId)).toList();
        List<Business> created = businesses.findCreatedInRange(start, end).stream()
                .filter(b -> userId == null || b.getCreatedBy().getId().equals(userId)).toList();
        List<Purchase> sold = purchases.findInRange(from, to).stream()
                .filter(p -> userId == null || p.getUser().getId().equals(userId)).toList();
        List<StatusChange> changes = statusChanges.findInRange(start, end).stream()
                .filter(s -> userId == null || s.getUser().getId().equals(userId)).toList();
        List<StatusChange> becameCustomer = changes.stream()
                .filter(s -> s.getToStatus() == BusinessStatus.CUSTOMER && !CUSTOMER.contains(s.getFromStatus()))
                .toList();
        List<Task> done = tasks.findCompletedInRange(TaskStatus.DONE, start, end).stream()
                .filter(t -> userId == null || t.getAssignedTo().getId().equals(userId)).toList();
        List<Interest> interestRows = interests.findUpdatedInRange(start, end).stream()
                .filter(i -> userId == null || i.getCreatedBy().getId().equals(userId)).toList();

        Set<Long> contacted = acts.stream().map(a -> a.getBusiness().getId()).collect(Collectors.toSet());
        long converted = becameCustomer.stream().map(s -> s.getBusiness().getId()).filter(contacted::contains).distinct().count();
        Double conversion = contacted.isEmpty() ? null
                : BigDecimal.valueOf(converted * 100.0 / contacted.size()).setScale(1, RoundingMode.HALF_UP).doubleValue();
        BigDecimal bottles = bottles(sold);

        Map<BusinessStatus, Long> pipeline = new EnumMap<>(BusinessStatus.class);
        for (BusinessStatus status : BusinessStatus.values()) {
            pipeline.put(status, 0L);
        }
        for (Object[] row : businesses.countByStatus(userId)) {
            pipeline.put((BusinessStatus) row[0], (Long) row[1]);
        }

        return new Report(from, to,
                created.size(),
                count(acts, ActivityType.CALL),
                acts.stream().filter(a -> a.getType() == ActivityType.CALL && !NOT_REACHED.contains(a.getResult())).count(),
                count(acts, ActivityType.VISIT),
                count(acts, ActivityType.MEETING),
                count(acts, ActivityType.SAMPLES),
                becameCustomer.size(),
                changes.stream().filter(s -> s.getToStatus() == BusinessStatus.LOST || s.getToStatus() == BusinessStatus.NOT_INTERESTED).count(),
                sold.size(),
                total(sold),
                done.size(),
                tasks.findWithStatusBefore(TaskStatus.OPEN, Instant.now(), userId).size(),
                contacted.size(),
                conversion,
                funnel(acts, becameCustomer.size(), bottles),
                bottles,
                byUser(userId, acts, created, sold, becameCustomer, done),
                byProduct(sold),
                flavorsSold(sold),
                flavorsOf(interestRows.stream().filter(i -> WANTED.contains(i.getStatus())).toList()),
                flavorsOf(interestRows.stream().filter(i -> i.getFeedback() == TastingFeedback.LIKED).toList()),
                flavorsOf(interestRows.stream()
                        .filter(i -> i.getFeedback() == TastingFeedback.DISLIKED || i.getStatus() == InterestStatus.NOT_INTERESTED).toList()),
                usages.countBusinessesByBrand().stream()
                        .map(row -> new BrandRow((String) row[0], (Boolean) row[1], (Long) row[2])).toList(),
                usages.countBusinessesByCompetitorFlavor().stream()
                        .map(row -> new FlavorRow((Long) row[0], (String) row[1], (String) row[2], (Long) row[3], null)).toList(),
                group(sold, p -> p.getBusiness().getType() == null ? "" : p.getBusiness().getType().getId().toString()),
                group(sold, p -> p.getBusiness().getDistrict() == null ? "" : p.getBusiness().getDistrict()),
                pipeline);
    }

    static Funnel funnel(List<Activity> acts, long becameClients, BigDecimal bottles) {
        List<Activity> calls = ofType(acts, ActivityType.CALL);
        List<Activity> meetings = ofType(acts, ActivityType.MEETING);
        List<Activity> visits = ofType(acts, ActivityType.VISIT);
        long noAnswer = calls.stream().filter(a -> NOT_REACHED.contains(a.getResult())).count();
        long yes = withResult(calls, SAID_YES);
        long no = withResult(calls, SAID_NO);
        return new Funnel(
                calls.size(), noAnswer, calls.size() - noAnswer, yes, no, calls.size() - noAnswer - yes - no,
                meetings.size(), withResult(meetings, SAID_YES), withResult(meetings, SAID_NO),
                visits.size(), withResult(visits, SAID_YES), withResult(visits, SAID_NO),
                count(acts, ActivityType.SAMPLES),
                calls.stream().map(a -> a.getBusiness().getId()).distinct().count(),
                calls.stream().filter(a -> !NOT_REACHED.contains(a.getResult())).map(a -> a.getBusiness().getId()).distinct().count(),
                acts.stream().filter(a -> a.getType() == ActivityType.MEETING || a.getType() == ActivityType.VISIT)
                        .map(a -> a.getBusiness().getId()).distinct().count(),
                becameClients,
                bottles.longValue());
    }

    private List<UserRow> byUser(Long userId, List<Activity> acts, List<Business> created, List<Purchase> sold,
            List<StatusChange> becameCustomer, List<Task> done) {
        return users.findAllByOrderByFullNameAsc().stream()
                .filter(u -> userId == null || u.getId().equals(userId))
                .map(u -> {
                    List<Activity> mine = acts.stream().filter(a -> a.getUser().getId().equals(u.getId())).toList();
                    List<Purchase> mySales = sold.stream().filter(p -> p.getUser().getId().equals(u.getId())).toList();
                    return new UserRow(u.getId(), u.getFullName(),
                            count(mine, ActivityType.CALL),
                            mine.stream().filter(a -> a.getType() == ActivityType.CALL && !NOT_REACHED.contains(a.getResult())).count(),
                            count(mine, ActivityType.VISIT),
                            count(mine, ActivityType.MEETING),
                            count(mine, ActivityType.SAMPLES),
                            created.stream().filter(b -> b.getCreatedBy().getId().equals(u.getId())).count(),
                            becameCustomer.stream().filter(s -> s.getUser().getId().equals(u.getId())).count(),
                            mySales.size(),
                            total(mySales),
                            bottles(mySales),
                            done.stream().filter(t -> t.getAssignedTo().getId().equals(u.getId())).count());
                })
                .filter(row -> row.calls() + row.visits() + row.meetings() + row.newLeads() + row.purchases() + row.tasksDone() > 0
                        || userId != null || isActive(row.userId()))
                .toList();
    }

    private boolean isActive(Long id) {
        return users.findById(id).map(User::isActive).orElse(false);
    }

    private static List<ProductRow> byProduct(List<Purchase> sold) {
        Map<String, ProductRow> rows = new LinkedHashMap<>();
        for (Purchase purchase : sold) {
            for (PurchaseItem item : purchase.getItems()) {
                String key = item.getProduct() != null ? "p" + item.getProduct().getId() : "d" + item.getDescription();
                ProductRow existing = rows.get(key);
                String ka = item.getProduct() != null ? item.getProduct().getNameKa() : item.getDescription();
                String en = item.getProduct() != null ? item.getProduct().getNameEn() : item.getDescription();
                rows.put(key, existing == null
                        ? new ProductRow(item.getProduct() == null ? null : item.getProduct().getId(), ka, en, item.getQuantity(), item.getLineTotal())
                        : new ProductRow(existing.productId(), ka, en, existing.quantity().add(item.getQuantity()), existing.total().add(item.getLineTotal())));
            }
        }
        return rows.values().stream().sorted(Comparator.comparing(ProductRow::quantity).reversed()).toList();
    }

    /** Bottles per flavor. A two-flavor syrup ("Dragon Fruit & Mango") counts for both flavors. */
    private static List<FlavorRow> flavorsSold(List<Purchase> sold) {
        Map<Long, Flavor> flavorById = new HashMap<>();
        Map<Long, BigDecimal> quantity = new HashMap<>();
        Map<Long, Set<Long>> buyers = new HashMap<>();
        for (Purchase purchase : sold) {
            for (PurchaseItem item : purchase.getItems()) {
                if (item.getProduct() == null) {
                    continue;
                }
                for (Flavor flavor : item.getProduct().getFlavors()) {
                    flavorById.put(flavor.getId(), flavor);
                    quantity.merge(flavor.getId(), item.getQuantity(), BigDecimal::add);
                    buyers.computeIfAbsent(flavor.getId(), k -> new java.util.HashSet<>()).add(purchase.getBusiness().getId());
                }
            }
        }
        return quantity.entrySet().stream()
                .map(e -> {
                    Flavor f = flavorById.get(e.getKey());
                    return new FlavorRow(f.getId(), f.getNameKa(), f.getNameEn(), buyers.get(e.getKey()).size(), e.getValue());
                })
                .sorted(Comparator.comparing(FlavorRow::quantity).reversed())
                .toList();
    }

    /** How many businesses each flavor appears for, through the flavor itself or one of our products. */
    private static List<FlavorRow> flavorsOf(Collection<Interest> rows) {
        Map<Long, Flavor> flavorById = new HashMap<>();
        Map<Long, Set<Long>> businessesBy = new HashMap<>();
        for (Interest interest : rows) {
            List<Flavor> named = new ArrayList<>();
            if (interest.getFlavor() != null) {
                named.add(interest.getFlavor());
            } else if (interest.getProduct() != null) {
                named.addAll(interest.getProduct().getFlavors());
            }
            for (Flavor flavor : named) {
                flavorById.put(flavor.getId(), flavor);
                businessesBy.computeIfAbsent(flavor.getId(), k -> new java.util.HashSet<>()).add(interest.getBusiness().getId());
            }
        }
        return businessesBy.entrySet().stream()
                .map(e -> {
                    Flavor f = flavorById.get(e.getKey());
                    return new FlavorRow(f.getId(), f.getNameKa(), f.getNameEn(), e.getValue().size(), null);
                })
                .sorted(Comparator.comparing(FlavorRow::count).reversed().thenComparing(FlavorRow::nameEn))
                .toList();
    }

    private static List<GroupRow> group(List<Purchase> sold, Function<Purchase, String> keyOf) {
        Map<String, List<Purchase>> grouped = new HashMap<>();
        for (Purchase purchase : sold) {
            grouped.computeIfAbsent(keyOf.apply(purchase), key -> new ArrayList<>()).add(purchase);
        }
        return grouped.entrySet().stream()
                .map(entry -> new GroupRow(entry.getKey(), entry.getValue().size(), total(entry.getValue())))
                .sorted(Comparator.comparing(GroupRow::total).reversed())
                .toList();
    }

    private static List<Activity> ofType(List<Activity> acts, ActivityType type) {
        return acts.stream().filter(a -> a.getType() == type).toList();
    }

    private static long withResult(List<Activity> acts, Set<ActivityResult> results) {
        return acts.stream().filter(a -> results.contains(a.getResult())).count();
    }

    private static long count(List<Activity> acts, ActivityType type) {
        return acts.stream().filter(a -> a.getType() == type).count();
    }

    private static BigDecimal total(List<Purchase> list) {
        return list.stream().map(Purchase::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Every unit sold counts as a bottle: the price list sells syrups by the bottle. */
    private static BigDecimal bottles(List<Purchase> list) {
        return list.stream().flatMap(p -> p.getItems().stream()).map(PurchaseItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
