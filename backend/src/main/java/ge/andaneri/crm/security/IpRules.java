package ge.andaneri.crm.security;

import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.domain.IpRule;
import ge.andaneri.crm.domain.IpRuleRepository;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.service.SettingsService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The whitelist and the blocklist, checked on every API request. Rules are kept in memory and reloaded
 * every half minute or right after a change, so the check costs no query.
 */
@Service
public class IpRules {

    private static final Duration CACHE = Duration.ofSeconds(30);

    private record Compiled(IpRule.Kind kind, byte[] network, int prefix, Instant expiresAt) {
    }

    private final IpRuleRepository repository;
    private final SettingsService settings;
    private volatile List<Compiled> cache;
    private volatile boolean whitelistOn;
    private volatile Instant loadedAt = Instant.EPOCH;

    public IpRules(IpRuleRepository repository, SettingsService settings) {
        this.repository = repository;
        this.settings = settings;
    }

    public boolean blocked(String ip) {
        return matchesAny(ip, IpRule.Kind.BLOCK);
    }

    public boolean allowed(String ip) {
        return matchesAny(ip, IpRule.Kind.ALLOW);
    }

    public boolean whitelistOn() {
        refreshIfOld();
        return whitelistOn;
    }

    public void reload() {
        loadedAt = Instant.EPOCH;
    }

    /** Blocks an address for a while after too many failed sign-ins. Does nothing if it is already blocked. */
    @Transactional
    public void autoBlock(String ip, int minutes, String note) {
        if (blocked(ip)) {
            return;
        }
        IpRule rule = new IpRule();
        rule.setPattern(ip);
        rule.setKind(IpRule.Kind.BLOCK);
        rule.setAutomatic(true);
        rule.setNote(note);
        rule.setExpiresAt(Instant.now().plus(Duration.ofMinutes(minutes)));
        repository.save(rule);
        reload();
    }

    @Transactional
    public IpRule add(String pattern, IpRule.Kind kind, String note, Instant expiresAt, User user) {
        IpRule rule = new IpRule();
        rule.setPattern(normalizePattern(pattern));
        rule.setKind(kind);
        rule.setNote(note == null || note.isBlank() ? null : note.trim());
        rule.setExpiresAt(expiresAt);
        rule.setCreatedBy(user);
        IpRule saved = repository.save(rule);
        reload();
        return saved;
    }

    @Transactional
    public void remove(Long id) {
        repository.deleteById(id);
        reload();
    }

    private boolean matchesAny(String ip, IpRule.Kind kind) {
        refreshIfOld();
        byte[] address = parse(ip);
        if (address == null) {
            return false;
        }
        Instant now = Instant.now();
        for (Compiled rule : cache) {
            if (rule.kind() == kind && (rule.expiresAt() == null || rule.expiresAt().isAfter(now)) && contains(rule, address)) {
                return true;
            }
        }
        return false;
    }

    private void refreshIfOld() {
        if (cache != null && loadedAt.plus(CACHE).isAfter(Instant.now())) {
            return;
        }
        List<Compiled> compiled = new ArrayList<>();
        for (IpRule rule : repository.findAll()) {
            String[] parts = rule.getPattern().split("/");
            byte[] network = parse(parts[0]);
            if (network == null) {
                continue;
            }
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : network.length * 8;
            compiled.add(new Compiled(rule.getKind(), network, prefix, rule.getExpiresAt()));
        }
        cache = compiled;
        whitelistOn = settings.isOn(SettingsService.IP_WHITELIST_ENABLED);
        loadedAt = Instant.now();
    }

    private static boolean contains(Compiled rule, byte[] address) {
        if (rule.network().length != address.length) {
            return false;
        }
        int full = rule.prefix() / 8;
        for (int i = 0; i < full; i++) {
            if (rule.network()[i] != address[i]) {
                return false;
            }
        }
        int rest = rule.prefix() % 8;
        if (rest == 0) {
            return true;
        }
        int mask = 0xFF << (8 - rest);
        return (rule.network()[full] & mask) == (address[full] & mask);
    }

    /** "185.12.3.4", "185.12.0.0/16", "2a02:1234::/32". Literal addresses only: never a host name, so never a DNS lookup. */
    public static String normalizePattern(String input) {
        String pattern = input == null ? "" : input.trim();
        if (!pattern.matches("[0-9a-fA-F:.]+(/\\d{1,3})?")) {
            throw ApiException.field("pattern", "format");
        }
        String[] parts = pattern.split("/");
        byte[] address = parse(parts[0]);
        if (address == null) {
            throw ApiException.field("pattern", "format");
        }
        if (parts.length > 1 && Integer.parseInt(parts[1]) > address.length * 8) {
            throw ApiException.field("pattern", "range");
        }
        return pattern.toLowerCase();
    }

    static byte[] parse(String ip) {
        if (ip == null || !ip.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    /** True when the address matches the pattern, for showing which rule covers an address. */
    public static boolean covers(String pattern, String ip) {
        String[] parts = pattern.split("/");
        byte[] network = parse(parts[0]);
        byte[] address = parse(ip);
        if (network == null || address == null) {
            return false;
        }
        int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : network.length * 8;
        return contains(new Compiled(IpRule.Kind.ALLOW, network, prefix, null), address);
    }
}
