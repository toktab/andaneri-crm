package ge.andaneri.crm.backup;

import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.service.AuditService;
import ge.andaneri.crm.service.SettingsService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps backups in the database for the retention period, so any admin can download one, logs who made and
 * downloaded each (user, address, browser, time), deletes them once they are older than the retention
 * setting, and says when the next one is due. Files are read with plain JDBC, so lists never load them.
 */
@Service
public class BackupService {

    public record BackupInfo(long id, String fileName, long sizeBytes, int businesses, int projects, String createdBy,
            Instant createdAt, Instant expiresAt, List<EventInfo> events) {
    }

    public record EventInfo(String action, String fileName, String username, String ip, String userAgent, Instant at) {
    }

    public record Status(Instant lastBackupAt, Instant nextDueAt, boolean due, boolean reminder, int intervalDays, int retentionDays) {
    }

    public record Content(long id, String fileName, byte[] bytes) {
    }

    private final JdbcTemplate jdbc;
    private final BackupBuilder builder;
    private final SettingsService settings;
    private final AuditService audit;

    public BackupService(JdbcTemplate jdbc, BackupBuilder builder, SettingsService settings, AuditService audit) {
        this.jdbc = jdbc;
        this.builder = builder;
        this.settings = settings;
        this.audit = audit;
    }

    public BackupInfo create(User user, String ip, String userAgent) {
        Instant now = Instant.now();
        BackupBuilder.Built built = builder.build(user, now);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into backups (file_name, size_bytes, sha256, content, businesses, projects, created_by_id, created_at)"
                            + " values (?, ?, ?, ?, ?, ?, ?, ?)", new String[] {"id"});
            ps.setString(1, built.fileName());
            ps.setLong(2, built.zip().length);
            ps.setString(3, sha256(built.zip()));
            ps.setBytes(4, built.zip());
            ps.setInt(5, built.businesses());
            ps.setInt(6, built.projects());
            ps.setLong(7, user.getId());
            ps.setObject(8, utc(now));
            return ps;
        }, key);
        long id = key.getKey().longValue();
        event(id, built.fileName(), "CREATED", user, ip, userAgent, now);
        audit.record(null, "Backup", id, "CREATED", built.fileName() + " (" + built.businesses() + ")", user);
        return list(now).stream().filter(b -> b.id() == id).findFirst().orElseThrow();
    }

    public List<BackupInfo> list(Instant now) {
        int retention = settings.getInt(SettingsService.BACKUP_RETENTION_DAYS);
        List<BackupInfo> backups = jdbc.query("select b.id, b.file_name, b.size_bytes, b.businesses, b.projects, b.created_at, u.full_name"
                + " from backups b left join users u on u.id = b.created_by_id order by b.created_at desc", (rs, i) -> {
                    Instant created = instant(rs, "created_at");
                    return new BackupInfo(rs.getLong("id"), rs.getString("file_name"), rs.getLong("size_bytes"), rs.getInt("businesses"),
                            rs.getInt("projects"), rs.getString("full_name"), created, created.plus(Duration.ofDays(retention)), new ArrayList<>());
                });
        Map<Long, List<EventInfo>> events = new LinkedHashMap<>();
        jdbc.query("select backup_id, action, file_name, username, ip, user_agent, created_at from backup_events"
                + " where backup_id is not null order by created_at desc", (ResultSet rs) -> {
                    events.computeIfAbsent(rs.getLong("backup_id"), k -> new ArrayList<>()).add(eventOf(rs));
                });
        return backups.stream().map(b -> new BackupInfo(b.id(), b.fileName(), b.sizeBytes(), b.businesses(), b.projects(), b.createdBy(),
                b.createdAt(), b.expiresAt(), events.getOrDefault(b.id(), List.of()))).toList();
    }

    /** Every make / download / delete, newest first, including for backups that no longer exist. */
    public List<EventInfo> log(int limit) {
        return jdbc.query("select action, file_name, username, ip, user_agent, created_at from backup_events order by created_at desc limit ?",
                (rs, i) -> eventOf(rs), Math.max(1, Math.min(limit, 500)));
    }

    public Content download(long id, User user, String ip, String userAgent) {
        List<Content> found = jdbc.query("select id, file_name, content from backups where id = ?",
                (rs, i) -> new Content(rs.getLong("id"), rs.getString("file_name"), rs.getBytes("content")), id);
        if (found.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BACKUP_EXPIRED");
        }
        Content content = found.get(0);
        event(id, content.fileName(), "DOWNLOADED", user, ip, userAgent, Instant.now());
        audit.record(null, "Backup", id, "DOWNLOADED", content.fileName() + " · " + ip, user);
        return content;
    }

    public void delete(long id, User user, String ip, String userAgent) {
        List<String> names = jdbc.queryForList("select file_name from backups where id = ?", String.class, id);
        if (names.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BACKUP_EXPIRED");
        }
        event(id, names.get(0), "DELETED", user, ip, userAgent, Instant.now());
        jdbc.update("delete from backups where id = ?", id);
        audit.record(null, "Backup", id, "DELETED", names.get(0), user);
    }

    public Status status(Instant now) {
        int interval = settings.getInt(SettingsService.BACKUP_INTERVAL_DAYS);
        Instant last = jdbc.query("select max(created_at) as last_at from backup_events where action = 'CREATED'",
                (ResultSet rs) -> rs.next() ? instant(rs, "last_at") : null);
        Instant next = last == null ? now : last.plus(Duration.ofDays(interval));
        return new Status(last, next, !now.isBefore(next), settings.isOn(SettingsService.BACKUP_REMINDER), interval,
                settings.getInt(SettingsService.BACKUP_RETENTION_DAYS));
    }

    @Scheduled(cron = "0 17 * * * *")
    public void cleanupHourly() {
        cleanup(Instant.now());
    }

    /** Deletes backups older than the retention period (the log of them stays). Returns how many. */
    public int cleanup(Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(settings.getInt(SettingsService.BACKUP_RETENTION_DAYS)));
        List<Map<String, Object>> old = jdbc.queryForList("select id, file_name from backups where created_at < ?", utc(cutoff));
        for (Map<String, Object> row : old) {
            long id = ((Number) row.get("id")).longValue();
            event(id, (String) row.get("file_name"), "EXPIRED", null, null, null, now);
            jdbc.update("delete from backups where id = ?", id);
        }
        return old.size();
    }

    private void event(Long backupId, String fileName, String action, User user, String ip, String userAgent, Instant at) {
        jdbc.update("insert into backup_events (backup_id, file_name, action, user_id, username, ip, user_agent, created_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?)",
                backupId, fileName, action, user == null ? null : user.getId(), user == null ? null : user.getUsername(), ip,
                userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length())), utc(at));
    }

    private static EventInfo eventOf(ResultSet rs) throws SQLException {
        return new EventInfo(rs.getString("action"), rs.getString("file_name"), rs.getString("username"), rs.getString("ip"),
                rs.getString("user_agent"), instant(rs, "created_at"));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
