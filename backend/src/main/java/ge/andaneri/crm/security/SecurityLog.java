package ge.andaneri.crm.security;

import ge.andaneri.crm.service.SettingsService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sign-in attempts, requests and change rows, written with plain JDBC so they never ride along in (or
 * roll back with) the transaction of the thing being logged. Requests and changes are written on a
 * background thread; sign-in attempts right away, because the brute-force check reads them.
 *
 * <p>Times are stored as UTC wall-clock values, the same way Hibernate stores every other instant here.
 */
@Component
public class SecurityLog {

    private static final Logger log = LoggerFactory.getLogger(SecurityLog.class);

    public record LoginEvent(long id, String username, Long userId, boolean success, String reason,
            String attemptedPassword, String ip, String userAgent, Instant createdAt) {
    }

    public record RequestEntry(long id, Long userId, String username, String method, String path, String query,
            int status, int durationMs, String ip, String userAgent, Instant createdAt) {
    }

    public record ChangeEntry(long id, Long businessId, String entity, Long entityId, String action, String summary,
            String changes, Long userId, String username, String ip, Instant createdAt) {
    }

    public record Page<T>(List<T> items, long total, int page, int size) {
    }

    private final JdbcTemplate jdbc;
    private final SettingsService settings;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "security-log");
        thread.setDaemon(true);
        return thread;
    });

    public SecurityLog(JdbcTemplate jdbc, SettingsService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    // ------------------------------------------------------------------ writing

    public void login(String username, Long userId, boolean success, String reason, String attemptedPassword, String ip, String userAgent) {
        jdbc.update("insert into login_events (username, user_id, success, reason, attempted_password, ip, user_agent, created_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?)",
                cut(username, 120), userId, success, reason, cut(attemptedPassword, 200), cut(ip, 64), cut(userAgent, 400), utc(Instant.now()));
    }

    public void request(Long userId, String username, String method, String path, String query, int status, int durationMs,
            String ip, String userAgent) {
        Instant at = Instant.now();
        writer.execute(() -> safely(() -> jdbc.update(
                "insert into request_logs (user_id, username, method, path, query_string, status, duration_ms, ip, user_agent, created_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, cut(username, 60), cut(method, 10), cut(path, 300), cut(query, 500), status, durationMs, cut(ip, 64),
                cut(userAgent, 300), utc(at))));
    }

    public void change(Long businessId, String entity, Long entityId, String action, String changes, Long userId, String ip) {
        Instant at = Instant.now();
        writer.execute(() -> safely(() -> jdbc.update(
                "insert into audit_entries (business_id, entity, entity_id, action, summary, changes, user_id, ip, created_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                businessId, cut(entity, 40), entityId, action, null, changes, userId, cut(ip, 64), utc(at))));
    }

    /** For tests: wait until everything queued so far has been written. */
    public void flush() {
        try {
            writer.submit(() -> { }).get();
        } catch (Exception ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void safely(Runnable write) {
        try {
            write.run();
        } catch (RuntimeException ex) {
            log.warn("Could not write to the security log: {}", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ brute-force check

    /** Wrong passwords for one account from one address: that account is slowed down, nobody else. */
    public int failuresSince(String ip, Instant since, String username) {
        Integer count = jdbc.queryForObject(
                "select count(*) from login_events where ip = ? and lower(username) = lower(?) and success = false"
                        + " and reason = 'BAD_PASSWORD' and created_at >= ?",
                Integer.class, ip, username, utc(since));
        return count == null ? 0 : count;
    }

    /**
     * Tries at names that do not exist, from one address. That is someone guessing their way in rather
     * than a colleague mistyping, and it is the only thing that blocks an address.
     */
    public int unknownAccountFailuresSince(String ip, Instant since) {
        Integer count = jdbc.queryForObject(
                "select count(*) from login_events where ip = ? and success = false and reason = 'UNKNOWN_USER' and created_at >= ?",
                Integer.class, ip, utc(since));
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------ reading (root only)

    public Page<LoginEvent> logins(Boolean success, String username, String ip, Long userId, Instant from, Instant to, int page, int size) {
        Where where = new Where();
        where.add(success != null, "success = ?", success);
        where.add(username != null && !username.isBlank(), "lower(username) like ?", like(username));
        where.add(ip != null && !ip.isBlank(), "ip like ?", like(ip));
        where.add(userId != null, "user_id = ?", userId);
        where.add(from != null, "created_at >= ?", utc(from));
        where.add(to != null, "created_at < ?", utc(to));
        return page("login_events", where, "created_at desc, id desc", page, size, (rs, i) -> new LoginEvent(
                rs.getLong("id"), rs.getString("username"), nullableLong(rs, "user_id"), rs.getBoolean("success"),
                rs.getString("reason"), rs.getString("attempted_password"), rs.getString("ip"), rs.getString("user_agent"),
                instant(rs, "created_at")));
    }

    public Page<RequestEntry> requests(Long userId, String ip, String method, String path, Integer status, Instant from, Instant to,
            int page, int size) {
        Where where = new Where();
        where.add(userId != null, "user_id = ?", userId);
        where.add(ip != null && !ip.isBlank(), "ip like ?", like(ip));
        where.add(method != null && !method.isBlank(), "method = ?", method == null ? null : method.toUpperCase());
        where.add(path != null && !path.isBlank(), "lower(path) like ?", like(path));
        where.add(status != null, "status = ?", status);
        where.add(from != null, "created_at >= ?", utc(from));
        where.add(to != null, "created_at < ?", utc(to));
        return page("request_logs", where, "created_at desc, id desc", page, size, (rs, i) -> new RequestEntry(
                rs.getLong("id"), nullableLong(rs, "user_id"), rs.getString("username"), rs.getString("method"),
                rs.getString("path"), rs.getString("query_string"), rs.getInt("status"), rs.getInt("duration_ms"),
                rs.getString("ip"), rs.getString("user_agent"), instant(rs, "created_at")));
    }

    public Page<ChangeEntry> changes(Long userId, String entity, String action, Long businessId, Instant from, Instant to,
            int page, int size) {
        Where where = new Where();
        where.add(userId != null, "a.user_id = ?", userId);
        where.add(entity != null && !entity.isBlank(), "a.entity = ?", entity);
        where.add(action != null && !action.isBlank(), "a.action = ?", action);
        where.add(businessId != null, "a.business_id = ?", businessId);
        where.add(from != null, "a.created_at >= ?", utc(from));
        where.add(to != null, "a.created_at < ?", utc(to));
        String base = "audit_entries a left join users u on u.id = a.user_id";
        long total = count(base, where);
        List<Object> args = new ArrayList<>(where.args);
        args.add(size);
        args.add(page * size);
        List<ChangeEntry> items = jdbc.query("select a.*, u.username from " + base + where.sql()
                        + " order by a.created_at desc, a.id desc limit ? offset ?",
                (rs, i) -> new ChangeEntry(rs.getLong("id"), nullableLong(rs, "business_id"), rs.getString("entity"),
                        nullableLong(rs, "entity_id"), rs.getString("action"), rs.getString("summary"), rs.getString("changes"),
                        nullableLong(rs, "user_id"), rs.getString("username"), rs.getString("ip"), instant(rs, "created_at")),
                args.toArray());
        return new Page<>(items, total, page, size);
    }

    public record IpActivity(String ip, Instant firstSeen, Instant lastSeen, long successfulLogins, long failedLogins,
            long requests, List<String> usernames) {
    }

    /** Every address seen signing in or making requests, most recent first. */
    public List<IpActivity> ipActivity(Long userId, int limit) {
        String userLogin = userId == null ? "" : " and user_id = " + userId;
        String userRequest = userId == null ? "" : " where user_id = " + userId;
        java.util.Map<String, long[]> counts = new java.util.LinkedHashMap<>();
        java.util.Map<String, Instant[]> seen = new java.util.HashMap<>();
        jdbc.query("select ip, min(created_at) first_seen, max(created_at) last_seen,"
                + " sum(case when success then 1 else 0 end) ok, sum(case when success then 0 else 1 end) failed"
                + " from login_events where ip is not null" + userLogin + " group by ip", rs -> {
                    String ip = rs.getString("ip");
                    counts.computeIfAbsent(ip, k -> new long[3]);
                    counts.get(ip)[0] = rs.getLong("ok");
                    counts.get(ip)[1] = rs.getLong("failed");
                    merge(seen, ip, instant(rs, "first_seen"), instant(rs, "last_seen"));
                });
        jdbc.query("select ip, count(*) n, min(created_at) first_seen, max(created_at) last_seen from request_logs"
                + (userRequest.isEmpty() ? " where ip is not null" : userRequest + " and ip is not null") + " group by ip", rs -> {
                    String ip = rs.getString("ip");
                    counts.computeIfAbsent(ip, k -> new long[3]);
                    counts.get(ip)[2] = rs.getLong("n");
                    merge(seen, ip, instant(rs, "first_seen"), instant(rs, "last_seen"));
                });
        java.util.Map<String, java.util.Set<String>> names = new java.util.HashMap<>();
        jdbc.query("select distinct ip, username from login_events where success = true and ip is not null" + userLogin, rs -> {
            names.computeIfAbsent(rs.getString("ip"), k -> new java.util.TreeSet<>()).add(rs.getString("username"));
        });
        jdbc.query("select distinct ip, username from request_logs where username is not null and ip is not null"
                + (userId == null ? "" : " and user_id = " + userId), rs -> {
            names.computeIfAbsent(rs.getString("ip"), k -> new java.util.TreeSet<>()).add(rs.getString("username"));
        });
        return counts.entrySet().stream()
                .map(e -> new IpActivity(e.getKey(), seen.get(e.getKey())[0], seen.get(e.getKey())[1], e.getValue()[0], e.getValue()[1],
                        e.getValue()[2], List.copyOf(names.getOrDefault(e.getKey(), java.util.Set.of()))))
                .sorted(java.util.Comparator.comparing(IpActivity::lastSeen).reversed())
                .limit(limit)
                .toList();
    }

    public record Overview(long failedLogins24h, long successfulLogins24h, long requests24h, long changes24h,
            long activeUsers24h, long distinctIps24h) {
    }

    public Overview overview() {
        Object since = utc(Instant.now().minus(1, ChronoUnit.DAYS));
        return new Overview(
                number("select count(*) from login_events where success = false and created_at >= ?", since),
                number("select count(*) from login_events where success = true and created_at >= ?", since),
                number("select count(*) from request_logs where created_at >= ?", since),
                number("select count(*) from audit_entries where created_at >= ?", since),
                number("select count(distinct user_id) from request_logs where user_id is not null and created_at >= ?", since),
                number("select count(distinct ip) from request_logs where created_at >= ?", since));
    }

    /** Every night: drop sign-in and request logs past the retention period. The change log is kept for good. */
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        Object before = utc(Instant.now().minus(settings.getInt(SettingsService.LOG_RETENTION_DAYS), ChronoUnit.DAYS));
        int logins = jdbc.update("delete from login_events where created_at < ?", before);
        int requests = jdbc.update("delete from request_logs where created_at < ?", before);
        if (logins + requests > 0) {
            log.info("Log retention: removed {} sign-in and {} request rows", logins, requests);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static final class Where {
        private final List<String> parts = new ArrayList<>();
        private final List<Object> args = new ArrayList<>();

        void add(boolean when, String condition, Object arg) {
            if (when) {
                parts.add(condition);
                args.add(arg);
            }
        }

        String sql() {
            return parts.isEmpty() ? "" : " where " + String.join(" and ", parts);
        }
    }

    private <T> Page<T> page(String table, Where where, String order, int page, int size,
            org.springframework.jdbc.core.RowMapper<T> mapper) {
        long total = count(table, where);
        List<Object> args = new ArrayList<>(where.args);
        args.add(size);
        args.add(page * size);
        List<T> items = jdbc.query("select * from " + table + where.sql() + " order by " + order + " limit ? offset ?", mapper, args.toArray());
        return new Page<>(items, total, page, size);
    }

    private long count(String from, Where where) {
        Long total = jdbc.queryForObject("select count(*) from " + from + where.sql(), Long.class, where.args.toArray());
        return total == null ? 0 : total;
    }

    private long number(String sql, Object arg) {
        Long value = jdbc.queryForObject(sql, Long.class, arg);
        return value == null ? 0 : value;
    }

    private static void merge(java.util.Map<String, Instant[]> seen, String ip, Instant first, Instant last) {
        Instant[] range = seen.computeIfAbsent(ip, k -> new Instant[] {first, last});
        if (first != null && (range[0] == null || first.isBefore(range[0]))) {
            range[0] = first;
        }
        if (last != null && (range[1] == null || last.isAfter(range[1]))) {
            range[1] = last;
        }
    }

    // Both of these also run for filters that are off (Where.add takes its value eagerly), so null is fine.
    static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String like(String value) {
        return value == null ? null : "%" + value.trim().toLowerCase() + "%";
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
