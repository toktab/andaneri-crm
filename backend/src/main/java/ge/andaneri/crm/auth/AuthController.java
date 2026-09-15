package ge.andaneri.crm.auth;

import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.security.ClientIp;
import ge.andaneri.crm.security.IpRules;
import ge.andaneri.crm.security.SecurityLog;
import ge.andaneri.crm.service.SettingsService;
import ge.andaneri.crm.web.UserDtos.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in. Every attempt is written to the sign-in log with its address and outcome; too many failures
 * from one address block it for a while; while the whitelist is on, only root may sign in from elsewhere.
 * Deliberately not one transaction: the log row of a refused attempt must survive the refusal.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    // Checked against when the username does not exist, so a wrong username takes as long as a wrong password.
    private final String dummyHash;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final CrmProperties properties;
    private final CurrentUser currentUser;
    private final SecurityLog securityLog;
    private final IpRules ipRules;
    private final SettingsService settings;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            CrmProperties properties, CurrentUser currentUser, SecurityLog securityLog, IpRules ipRules, SettingsService settings) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.currentUser = currentUser;
        this.securityLog = securityLog;
        this.ipRules = ipRules;
        this.settings = settings;
        this.dummyHash = passwordEncoder.encode("no-such-user-" + System.nanoTime());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserDto user) {
    }

    public record PasswordChange(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = ClientIp.of(http);
        String agent = ClientIp.userAgent(http);
        String username = request.username().trim();
        boolean local = ClientIp.isLoopback(ip);
        int maxFailures = settings.getInt(SettingsService.MAX_FAILED_LOGINS);
        int lockMinutes = settings.getInt(SettingsService.LOCK_MINUTES);
        Instant window = Instant.now().minus(Duration.ofMinutes(lockMinutes));

        if (!local && securityLog.failuresSince(ip, window) >= maxFailures) {
            ipRules.autoBlock(ip, lockMinutes, maxFailures + " failed sign-ins");
            securityLog.login(username, null, false, "RATE_LIMITED", null, ip, agent);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
        }

        User user = users.findByUsernameIgnoreCase(username).orElse(null);
        boolean matches = passwordEncoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            securityLog.login(username, user == null ? null : user.getId(), false, user == null ? "UNKNOWN_USER" : "BAD_PASSWORD",
                    attempted(request.password()), ip, agent);
            if (!local && securityLog.failuresSince(ip, window) >= maxFailures) {
                ipRules.autoBlock(ip, lockMinutes, maxFailures + " failed sign-ins");
            }
            throw ApiException.unauthorized("BAD_CREDENTIALS");
        }
        if (!user.isActive()) {
            securityLog.login(username, user.getId(), false, "DISABLED", null, ip, agent);
            throw ApiException.unauthorized("ACCOUNT_DISABLED");
        }
        boolean rootBypass = user.isRoot() && properties.rootBypassWhitelist();
        if (!local && ipRules.whitelistOn() && !ipRules.allowed(ip) && !rootBypass) {
            securityLog.login(username, user.getId(), false, "NOT_WHITELISTED", null, ip, agent);
            throw new ApiException(HttpStatus.FORBIDDEN, "IP_NOT_ALLOWED");
        }

        securityLog.login(user.getUsername(), user.getId(), true, "OK", null, ip, agent);
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(ip);
        users.save(user);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(Math.max(1, properties.tokenHours())));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("andaneri-crm")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", List.of(user.getRole().name()))
                .claim("tv", user.getTokenVersion())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new LoginResponse(token, expiresAt, UserDto.of(user));
    }

    @GetMapping("/me")
    public UserDto me() {
        return UserDto.of(currentUser.require());
    }

    @PostMapping("/password")
    @Transactional
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChange request) {
        User user = currentUser.require();
        if (user.isRoot()) {
            // Root's password lives in ROOT_PASSWORD and would be reset on the next start anyway.
            throw ApiException.badRequest("ROOT_PASSWORD_FROM_ENVIRONMENT");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.field("currentPassword", "wrong");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
        return ResponseEntity.noContent().build();
    }

    /** What the sign-in log keeps of a wrong password: the full text only if LOG_FULL_FAILED_PASSWORDS is on. */
    private String attempted(String password) {
        if (properties.logFullFailedPasswords()) {
            return password;
        }
        int length = password.length();
        if (length <= 2) {
            return "•".repeat(length) + " (" + length + ")";
        }
        return password.charAt(0) + "•".repeat(Math.min(length - 2, 20)) + password.charAt(length - 1) + " (" + length + ")";
    }
}
