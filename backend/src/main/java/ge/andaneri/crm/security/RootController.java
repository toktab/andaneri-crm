package ge.andaneri.crm.security;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.domain.IpRule;
import ge.andaneri.crm.domain.IpRuleRepository;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.service.SettingsService;
import ge.andaneri.crm.web.UserDtos.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The security centre, for the root account only: sign-ins, addresses, requests, changes, sessions, IP rules. */
@RestController
@RequestMapping("/api/root")
public class RootController {

    private final CurrentUser currentUser;
    private final SecurityLog securityLog;
    private final IpRules ipRules;
    private final IpRuleRepository ruleRepository;
    private final SettingsService settings;
    private final UserRepository users;

    public RootController(CurrentUser currentUser, SecurityLog securityLog, IpRules ipRules, IpRuleRepository ruleRepository,
            SettingsService settings, UserRepository users) {
        this.currentUser = currentUser;
        this.securityLog = securityLog;
        this.ipRules = ipRules;
        this.ruleRepository = ruleRepository;
        this.settings = settings;
        this.users = users;
    }

    public record OverviewDto(SecurityLog.Overview counts, String yourIp, boolean whitelistOn, long liveBlocks,
            List<SecurityLog.LoginEvent> recentFailures) {
    }

    public record RuleDto(Long id, String pattern, IpRule.Kind kind, String note, boolean automatic, Instant expiresAt,
            String createdBy, Instant createdAt, boolean live) {
        static RuleDto of(IpRule rule) {
            return new RuleDto(rule.getId(), rule.getPattern(), rule.getKind(), rule.getNote(), rule.isAutomatic(), rule.getExpiresAt(),
                    rule.getCreatedBy() == null ? null : rule.getCreatedBy().getUsername(), rule.getCreatedAt(), rule.isLive(Instant.now()));
        }
    }

    public record RuleRequest(@NotBlank String pattern, @NotNull IpRule.Kind kind, String note, Instant expiresAt) {
    }

    public record IpDto(SecurityLog.IpActivity activity, String rule) {
    }

    @GetMapping("/overview")
    public OverviewDto overview(HttpServletRequest request) {
        currentUser.requireRoot();
        long blocks = ruleRepository.findAll().stream().filter(r -> r.getKind() == IpRule.Kind.BLOCK && r.isLive(Instant.now())).count();
        return new OverviewDto(securityLog.overview(), ClientIp.of(request), ipRules.whitelistOn(), blocks,
                securityLog.logins(false, null, null, null, null, null, 0, 10).items());
    }

    @GetMapping("/logins")
    public SecurityLog.Page<SecurityLog.LoginEvent> logins(
            @RequestParam(required = false) Boolean success, @RequestParam(required = false) String username,
            @RequestParam(required = false) String ip, @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        currentUser.requireRoot();
        return securityLog.logins(success, username, ip, userId, from, to, Math.max(page, 0), clamp(size));
    }

    @GetMapping("/requests")
    public SecurityLog.Page<SecurityLog.RequestEntry> requests(
            @RequestParam(required = false) Long userId, @RequestParam(required = false) String ip,
            @RequestParam(required = false) String method, @RequestParam(required = false) String path,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        currentUser.requireRoot();
        return securityLog.requests(userId, ip, method, path, status, from, to, Math.max(page, 0), clamp(size));
    }

    @GetMapping("/changes")
    public SecurityLog.Page<SecurityLog.ChangeEntry> changes(
            @RequestParam(required = false) Long userId, @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action, @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        currentUser.requireRoot();
        return securityLog.changes(userId, entity, action, businessId, from, to, Math.max(page, 0), clamp(size));
    }

    @GetMapping("/ips")
    @Transactional(readOnly = true)
    public List<IpDto> ips(@RequestParam(required = false) Long userId, @RequestParam(defaultValue = "300") int limit) {
        currentUser.requireRoot();
        List<IpRule> rules = ruleRepository.findAll();
        return securityLog.ipActivity(userId, Math.min(Math.max(limit, 1), 2000)).stream()
                .map(activity -> new IpDto(activity, ruleFor(rules, activity.ip())))
                .toList();
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<UserDto> users() {
        currentUser.requireRoot();
        return users.findAllByOrderByFullNameAsc().stream().map(UserDto::of).toList();
    }

    /** Signs a user out on every device: their existing tokens stop working at once. */
    @PostMapping("/users/{id}/revoke-sessions")
    @Transactional
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        currentUser.requireRoot();
        User user = users.findById(id).orElseThrow(ApiException::notFound);
        user.setTokenVersion(user.getTokenVersion() + 1);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ip-rules")
    @Transactional(readOnly = true)
    public List<RuleDto> rules() {
        currentUser.requireRoot();
        return ruleRepository.findAllForList().stream().map(RuleDto::of).toList();
    }

    @PostMapping("/ip-rules")
    public RuleDto addRule(@Valid @RequestBody RuleRequest request) {
        User root = currentUser.requireRoot();
        return RuleDto.of(ipRules.add(request.pattern(), request.kind(), request.note(), request.expiresAt(), root));
    }

    @DeleteMapping("/ip-rules/{id}")
    public ResponseEntity<Void> removeRule(@PathVariable Long id) {
        currentUser.requireRoot();
        ipRules.remove(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public Map<String, Integer> securitySettings() {
        currentUser.requireRoot();
        return settings.security();
    }

    /** Switching the whitelist on also allows the address it was switched on from, so the change cannot lock the root out. */
    @PutMapping("/settings")
    public Map<String, Integer> updateSecuritySettings(@RequestBody Map<String, Integer> values, HttpServletRequest request) {
        User root = currentUser.requireRoot();
        String ip = ClientIp.of(request);
        if (Integer.valueOf(1).equals(values.get(SettingsService.IP_WHITELIST_ENABLED)) && !ClientIp.isLoopback(ip) && !ipRules.allowed(ip)) {
            ipRules.add(ip, IpRule.Kind.ALLOW, "added when the whitelist was switched on", null, root);
        }
        Map<String, Integer> result = settings.updateSecurity(values);
        ipRules.reload();
        return result;
    }

    private static String ruleFor(List<IpRule> rules, String ip) {
        Instant now = Instant.now();
        for (IpRule rule : rules) {
            if (rule.getKind() == IpRule.Kind.BLOCK && rule.isLive(now) && IpRules.covers(rule.getPattern(), ip)) {
                return "BLOCK";
            }
        }
        for (IpRule rule : rules) {
            if (rule.getKind() == IpRule.Kind.ALLOW && rule.isLive(now) && IpRules.covers(rule.getPattern(), ip)) {
                return "ALLOW";
            }
        }
        return null;
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), 500);
    }
}
