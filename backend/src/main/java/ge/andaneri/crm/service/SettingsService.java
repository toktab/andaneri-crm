package ge.andaneri.crm.service;

import ge.andaneri.crm.domain.Setting;
import ge.andaneri.crm.domain.SettingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team-wide numbers changed without a redeploy. Sales settings are for admins; security settings are
 * for root only. Unknown keys and out-of-range values are ignored.
 */
@Service
public class SettingsService {

    /** Days without contact before an open lead shows up as "not contacted recently". */
    public static final String STALE_DAYS = "stale_days";
    /** Days between orders assumed for a customer with fewer than two purchases and no interval of their own. */
    public static final String REORDER_DAYS = "reorder_days";
    /** How many days ahead the dashboard's "upcoming" list looks. */
    public static final String UPCOMING_DAYS = "upcoming_days";

    /** Admins are asked to make a backup when the last one is this many days old. */
    public static final String BACKUP_INTERVAL_DAYS = "backup_interval_days";
    /** Days a backup stays downloadable on the server before it is deleted. */
    public static final String BACKUP_RETENTION_DAYS = "backup_retention_days";
    /** 1: show admins the "backup due" reminder. */
    public static final String BACKUP_REMINDER = "backup_reminder";

    /** 1: only addresses on the whitelist may use the API. */
    public static final String IP_WHITELIST_ENABLED = "ip_whitelist_enabled";
    /** Failed sign-ins from one address, within LOCK_MINUTES, before that address is blocked. */
    public static final String MAX_FAILED_LOGINS = "max_failed_logins";
    public static final String LOCK_MINUTES = "lock_minutes";
    /** Sign-in and request logs older than this are deleted every night. The change log is kept. */
    public static final String LOG_RETENTION_DAYS = "log_retention_days";
    /** 1: every API request is written to the request log. */
    public static final String REQUEST_LOG_ENABLED = "request_log_enabled";

    private record Definition(int fallback, int min, int max) {
    }

    private static final Map<String, Definition> SALES = new LinkedHashMap<>();
    private static final Map<String, Definition> SECURITY = new LinkedHashMap<>();

    static {
        SALES.put(STALE_DAYS, new Definition(14, 1, 365));
        SALES.put(REORDER_DAYS, new Definition(21, 1, 365));
        SALES.put(UPCOMING_DAYS, new Definition(7, 1, 60));
        SALES.put(BACKUP_INTERVAL_DAYS, new Definition(7, 1, 90));
        SALES.put(BACKUP_RETENTION_DAYS, new Definition(14, 1, 365));
        SALES.put(BACKUP_REMINDER, new Definition(1, 0, 1));
        SECURITY.put(IP_WHITELIST_ENABLED, new Definition(0, 0, 1));
        SECURITY.put(MAX_FAILED_LOGINS, new Definition(10, 3, 100));
        SECURITY.put(LOCK_MINUTES, new Definition(15, 1, 1440));
        SECURITY.put(LOG_RETENTION_DAYS, new Definition(180, 7, 3650));
        SECURITY.put(REQUEST_LOG_ENABLED, new Definition(1, 0, 1));
    }

    private final SettingRepository settings;

    public SettingsService(SettingRepository settings) {
        this.settings = settings;
    }

    public int getInt(String key) {
        Definition definition = SALES.containsKey(key) ? SALES.get(key) : SECURITY.get(key);
        return settings.findById(key)
                .map(setting -> parse(setting.getValue(), definition.fallback()))
                .orElse(definition.fallback());
    }

    public boolean isOn(String key) {
        return getInt(key) == 1;
    }

    /** The sales settings, as shown to everyone in lookups. */
    public Map<String, Integer> all() {
        return values(SALES);
    }

    public Map<String, Integer> security() {
        return values(SECURITY);
    }

    @Transactional
    public Map<String, Integer> update(Map<String, Integer> values) {
        write(values, SALES);
        return all();
    }

    @Transactional
    public Map<String, Integer> updateSecurity(Map<String, Integer> values) {
        write(values, SECURITY);
        return security();
    }

    private Map<String, Integer> values(Map<String, Definition> group) {
        Map<String, Integer> values = new LinkedHashMap<>();
        group.keySet().forEach(key -> values.put(key, getInt(key)));
        return values;
    }

    private void write(Map<String, Integer> values, Map<String, Definition> group) {
        values.forEach((key, value) -> {
            Definition definition = group.get(key);
            if (definition == null || value == null || value < definition.min() || value > definition.max()) {
                return;
            }
            Setting setting = settings.findById(key).orElseGet(() -> new Setting(key, value.toString()));
            setting.setValue(value.toString());
            settings.save(setting);
        });
    }

    private static int parse(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
