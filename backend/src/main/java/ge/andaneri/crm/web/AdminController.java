package ge.andaneri.crm.web;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.domain.AuditEntry;
import ge.andaneri.crm.domain.AuditEntryRepository;
import ge.andaneri.crm.domain.Role;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.service.AuditService;
import ge.andaneri.crm.service.SettingsService;
import ge.andaneri.crm.web.UserDtos.UserCreate;
import ge.andaneri.crm.web.UserDtos.UserDto;
import ge.andaneri.crm.web.UserDtos.UserRef;
import ge.andaneri.crm.web.UserDtos.UserUpdate;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Team accounts, team-wide settings and the audit trail. Users are deactivated, never deleted: their history stays theirs. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ge.andaneri.crm.auth.ProtectedAccounts protectedAccounts;
    private final CurrentUser currentUser;
    private final SettingsService settings;
    private final AuditEntryRepository auditEntries;
    private final AuditService audit;
    private final ge.andaneri.crm.domain.IpRuleRepository ipRules;
    private final ge.andaneri.crm.security.IpRules ipRuleCache;

    public AdminController(UserRepository users, PasswordEncoder passwordEncoder, CurrentUser currentUser,
            SettingsService settings, AuditEntryRepository auditEntries, AuditService audit, ge.andaneri.crm.auth.ProtectedAccounts protectedAccounts, ge.andaneri.crm.domain.IpRuleRepository ipRules, ge.andaneri.crm.security.IpRules ipRuleCache) {
        this.ipRules = ipRules;
        this.ipRuleCache = ipRuleCache;
        this.protectedAccounts = protectedAccounts;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.settings = settings;
        this.auditEntries = auditEntries;
        this.audit = audit;
    }

    public record AuditDto(Long id, Long businessId, String entity, Long entityId, String action, String summary,
            UserRef user, Instant createdAt) {
        static AuditDto of(AuditEntry entry) {
            return new AuditDto(entry.getId(), entry.getBusinessId(), entry.getEntity(), entry.getEntityId(),
                    entry.getAction(), entry.getSummary(), UserRef.of(entry.getUser()), entry.getCreatedAt());
        }
    }

    /** The root account is listed only to root itself. */
    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<UserDto> users() {
        User admin = currentUser.requireAdmin();
        return users.findAllByOrderByFullNameAsc().stream()
                .filter(user -> admin.isRoot() || !user.isRoot())
                .map(UserDto::of).toList();
    }

    @PostMapping("/users")
    @Transactional
    public UserDto create(@Valid @RequestBody UserCreate request) {
        User admin = currentUser.requireAdmin();
        // Root comes from ROOT_USERNAME / ROOT_PASSWORD only.
        if (request.role() == Role.ROOT) {
            throw ApiException.forbidden();
        }
        if (users.findByUsernameIgnoreCase(request.username()).isPresent()) {
            throw ApiException.field("username", "taken");
        }
        User user = new User();
        user.setUsername(request.username().trim());
        user.setFullName(request.fullName().trim());
        user.setPhone(CatalogController.blankToNull(request.phone()));
        user.setRole(request.role());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCreatedAt(Instant.now());
        user = users.save(user);
        audit.record(null, "User", user.getId(), "CREATED", user.getUsername() + " (" + user.getRole() + ")", admin);
        return UserDto.of(user);
    }

    @PutMapping("/users/{id}")
    @Transactional
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UserUpdate request) {
        User admin = currentUser.requireAdmin();
        User user = users.findById(id).orElseThrow(ApiException::notFound);
        boolean active = request.active() == null ? user.isActive() : request.active();
        // The key accounts are managed through the environment: nobody changes their role, switches them
        // off or sets their password here - that is what keeps a way back in after any lockout.
        if (user.isRoot()) {
            if (!admin.isRoot() || !active || request.role() != Role.ROOT || (request.password() != null && !request.password().isBlank())) {
                throw ApiException.forbidden();
            }
        } else if (protectedAccounts.isProtected(user)) {
            // The first admin: its password comes from ADMIN_PASSWORD, and it cannot be switched off here.
            if (!active || request.role() != user.getRole() || (request.password() != null && !request.password().isBlank())) {
                throw ApiException.badRequest("PROTECTED_ACCOUNT");
            }
        } else if (request.role() == Role.ROOT) {
            throw ApiException.forbidden();
        }
        // An admin cannot lock themselves out; another admin has to do it.
        if (user.getId().equals(admin.getId()) && (!active || request.role() != admin.getRole())) {
            throw ApiException.badRequest("CANNOT_DEMOTE_SELF");
        }
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 8) {
                throw ApiException.field("password", "length");
            }
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        String before = user.getRole() + (user.isActive() ? "" : " inactive");
        user.setFullName(request.fullName().trim());
        user.setPhone(CatalogController.blankToNull(request.phone()));
        user.setRole(request.role());
        user.setActive(active);
        audit.record(null, "User", id, "UPDATED",
                user.getUsername() + ": " + before + " -> " + user.getRole() + (user.isActive() ? "" : " inactive"), admin);
        return UserDto.of(user);
    }

    /** Addresses the server is turning away right now, so an admin can lift one without hunting for root. */
    @GetMapping("/ip-blocks")
    public List<BlockDto> blocks(jakarta.servlet.http.HttpServletRequest http) {
        currentUser.requireAdmin();
        Instant now = Instant.now();
        return ipRules.findAll().stream()
                .filter(rule -> rule.getKind() == ge.andaneri.crm.domain.IpRule.Kind.BLOCK && rule.isLive(now))
                .map(rule -> new BlockDto(rule.getId(), rule.getPattern(), rule.getNote(), rule.isAutomatic(),
                        rule.getCreatedAt(), rule.getExpiresAt(),
                        ge.andaneri.crm.security.IpRules.covers(rule.getPattern(), ge.andaneri.crm.security.ClientIp.of(http))))
                .toList();
    }

    /** Lifts one block. The sign-in log keeps why it was there. */
    @DeleteMapping("/ip-blocks/{id}")
    public ResponseEntity<Void> unblock(@PathVariable Long id) {
        User admin = currentUser.requireAdmin();
        ge.andaneri.crm.domain.IpRule rule = ipRules.findById(id).orElseThrow(ApiException::notFound);
        if (rule.getKind() != ge.andaneri.crm.domain.IpRule.Kind.BLOCK) {
            throw ApiException.forbidden();
        }
        ipRules.delete(rule);
        ipRuleCache.reload();
        audit.record(null, "IpRule", id, "DELETED", "unblocked " + rule.getPattern(), admin);
        return ResponseEntity.noContent().build();
    }

    public record BlockDto(Long id, String pattern, String note, boolean automatic, Instant createdAt, Instant expiresAt,
            boolean coversYou) {
    }

    @PutMapping("/settings")
    public Map<String, Integer> updateSettings(@RequestBody Map<String, Integer> values) {
        currentUser.requireAdmin();
        return settings.update(values);
    }

    @GetMapping("/audit")
    @Transactional(readOnly = true)
    public List<AuditDto> audit(@RequestParam(defaultValue = "200") int limit) {
        currentUser.requireAdmin();
        return auditEntries.findRecent(PageRequest.of(0, Math.min(Math.max(limit, 1), 1000))).stream().map(AuditDto::of).toList();
    }
}
