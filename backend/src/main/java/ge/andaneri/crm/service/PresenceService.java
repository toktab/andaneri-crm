package ge.andaneri.crm.service;

import ge.andaneri.crm.domain.User;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Who is working where, right now. Call mode says "I am on this one" every half minute while it is open,
 * and a supervisor's screen reads it back: "Nino is on Bar X". Nothing is written down - it lives in
 * memory for a minute and a half and then it is gone, because it is only ever about this moment.
 */
@Service
public class PresenceService {

    /** A heartbeat older than this is somebody who closed the tab. */
    private static final Duration ALIVE = Duration.ofSeconds(90);

    /** Where one person is, and since when. */
    public record Where(Long userId, String userName, Long businessId, String businessName, Instant since, Instant lastSeen) {
    }

    private final Map<Long, Where> byUser = new ConcurrentHashMap<>();

    /** The person is on this business now. Returns everyone else who is on the same one. */
    public List<Where> seen(User user, Long businessId, String businessName) {
        Instant now = Instant.now();
        byUser.compute(user.getId(), (id, previous) -> {
            Instant since = previous != null && businessId != null && businessId.equals(previous.businessId())
                    ? previous.since() : now;
            return new Where(id, user.getFullName(), businessId, businessName, since, now);
        });
        return businessId == null ? List.of() : othersOn(businessId, user.getId());
    }

    /** Somebody closed call mode or signed out. */
    public void left(Long userId) {
        byUser.remove(userId);
    }

    /** Everyone whose heartbeat is still fresh, the longest-standing first. */
    public List<Where> live() {
        Instant cutoff = Instant.now().minus(ALIVE);
        List<Where> out = new ArrayList<>();
        for (Where where : byUser.values()) {
            if (where.lastSeen().isAfter(cutoff)) {
                out.add(where);
            } else {
                byUser.remove(where.userId());
            }
        }
        out.sort(Comparator.comparing(Where::since));
        return out;
    }

    /** Who else is on this business at this moment. */
    public List<Where> othersOn(Long businessId, Long exceptUserId) {
        return live().stream()
                .filter(where -> businessId.equals(where.businessId()) && !where.userId().equals(exceptUserId))
                .toList();
    }

    /** Where this person is right now, or null. */
    public Where of(Long userId) {
        return live().stream().filter(where -> where.userId().equals(userId)).findFirst().orElse(null);
    }
}
