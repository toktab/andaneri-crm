package ge.andaneri.crm.web;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.security.SecurityLog;
import ge.andaneri.crm.security.SecurityLog.ChangeEntry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * "What did I do lately": one person's own changes, newest first, as readable entries. Built from the same
 * change log the security centre shows, with the object named (which business, which contact) and the
 * field-by-field before / after. Everyone sees their own; supervisors can look at a colleague's.
 */
@RestController
@RequestMapping("/api")
public class HistoryController {

    /** {@code changes}: field name to [before, after]; either side null when the object was added or deleted. */
    public record HistoryItem(long id, Instant at, String action, String entity, Long entityId, Long businessId,
            String businessName, String objectName, String summary, Map<String, List<String>> changes) {
    }

    public record HistoryPage(List<HistoryItem> items, int page, boolean hasMore, Long userId, String userName) {
    }

    private static final Set<String> RAW_ACTIONS = Set.of("INSERT", "UPDATE", "DELETE");
    /** Bookkeeping, or covered by a clearer entry (a status change is already the business's "status" field). */
    private static final Set<String> SKIPPED_ENTITIES = Set.of("StatusChange", "PushSubscription", "AuditEntry");
    private static final Set<String> SKIPPED_FIELDS = Set.of("createdAt", "createdBy", "updatedAt", "version", "remindedAt",
            "tokenVersion", "calendarToken", "passwordHash", "lastLoginAt", "lastLoginIp", "user", "author",
            // The entry already names and links its business.
            "business");
    private static final List<String> NAME_FIELDS = List.of("name", "title", "label", "description", "body", "summary");

    private final SecurityLog securityLog;
    private final BusinessRepository businesses;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final JsonMapper json;

    public HistoryController(SecurityLog securityLog, BusinessRepository businesses, UserRepository users, CurrentUser currentUser,
            JsonMapper json) {
        this.securityLog = securityLog;
        this.businesses = businesses;
        this.users = users;
        this.currentUser = currentUser;
        this.json = json;
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public HistoryPage history(@RequestParam(required = false) Long userId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "60") int size) {
        User me = currentUser.require();
        Long whose = userId == null ? me.getId() : userId;
        if (!whose.equals(me.getId())) {
            currentUser.requireSupervisor();
        }
        User person = users.findById(whose).orElseThrow(ApiException::notFound);
        int safeSize = Math.max(10, Math.min(size, 200));
        List<ChangeEntry> raw = securityLog.changes(whose, null, null, null, null, null, Math.max(page, 0), safeSize).items();

        List<HistoryItem> items = new ArrayList<>();
        List<ChangeEntry> described = new ArrayList<>();
        for (ChangeEntry entry : raw) {
            if (SKIPPED_ENTITIES.contains(entry.entity())) {
                continue;
            }
            if (!RAW_ACTIONS.contains(entry.action())) {
                described.add(entry);
                continue;
            }
            Map<String, List<String>> changes = parse(entry.changes());
            if ("UPDATE".equals(entry.action()) && changes.isEmpty()) {
                continue;
            }
            items.add(new HistoryItem(entry.id(), entry.createdAt(), entry.action(), entry.entity(), entry.entityId(),
                    businessIdOf(entry), null, nameFrom(changes), null, changes));
        }
        // The readable lines written by the services ("moved 10:00 -> 12:00", "merged ...") join the matching
        // before / after entry; the ones with nothing to join (a merge, an import) stand on their own.
        for (ChangeEntry entry : described) {
            int match = matching(items, entry);
            if (match >= 0) {
                if (entry.summary() != null && items.get(match).summary() == null) {
                    HistoryItem item = items.get(match);
                    items.set(match, new HistoryItem(item.id(), item.at(), item.action(), item.entity(), item.entityId(), item.businessId(),
                            null, item.objectName(), entry.summary(), item.changes()));
                }
            } else {
                items.add(new HistoryItem(entry.id(), entry.createdAt(), entry.action(), entry.entity(), entry.entityId(),
                        businessIdOf(entry), null, null, entry.summary(), Map.of()));
            }
        }

        Map<Long, String> names = businesses.findAllById(items.stream().map(HistoryItem::businessId).filter(Objects::nonNull)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Business::getId, Business::getName));
        List<HistoryItem> named = items.stream()
                .map(i -> new HistoryItem(i.id(), i.at(), i.action(), i.entity(), i.entityId(), i.businessId(),
                        i.businessId() == null ? null : names.get(i.businessId()),
                        i.objectName() != null ? i.objectName() : "Business".equals(i.entity()) && i.businessId() != null ? names.get(i.businessId()) : null,
                        i.summary(), i.changes()))
                .sorted((a, b) -> b.at().compareTo(a.at()))
                .toList();
        return new HistoryPage(named, Math.max(page, 0), raw.size() == safeSize, person.getId(), person.getFullName());
    }

    private static Long businessIdOf(ChangeEntry entry) {
        return entry.businessId() != null ? entry.businessId() : "Business".equals(entry.entity()) ? entry.entityId() : null;
    }

    private static int matching(List<HistoryItem> items, ChangeEntry entry) {
        for (int i = 0; i < items.size(); i++) {
            HistoryItem item = items.get(i);
            if (item.entity().equals(entry.entity()) && Objects.equals(item.entityId(), entry.entityId())
                    && Duration.between(item.at(), entry.createdAt()).abs().getSeconds() <= 5) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, List<String>> parse(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, List<String>> all = json.readValue(text, new TypeReference<Map<String, List<String>>>() { });
            Map<String, List<String>> kept = new LinkedHashMap<>();
            all.forEach((field, values) -> {
                if (!SKIPPED_FIELDS.contains(field) && values != null && values.size() == 2) {
                    kept.put(field, values);
                }
            });
            return kept;
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private static String nameFrom(Map<String, List<String>> changes) {
        for (String field : NAME_FIELDS) {
            List<String> values = changes.get(field);
            if (values != null) {
                String value = values.get(1) != null ? values.get(1) : values.get(0);
                if (value != null && !value.isBlank()) {
                    return value.length() > 80 ? value.substring(0, 79) + "…" : value;
                }
            }
        }
        return null;
    }
}
