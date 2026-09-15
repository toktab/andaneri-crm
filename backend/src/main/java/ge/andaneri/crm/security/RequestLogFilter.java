package ge.andaneri.crm.security;

import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.service.SettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Writes every API request to the request log: who, from where, what, how it ended, how long it took. */
public class RequestLogFilter extends OncePerRequestFilter {

    private final SecurityLog securityLog;
    private final SettingsService settings;
    private final UserRepository users;
    private final Map<Long, String> usernames = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;
    private volatile Instant checkedAt = Instant.EPOCH;

    public RequestLogFilter(SecurityLog securityLog, SettingsService settings, UserRepository users) {
        this.securityLog = securityLog;
        this.settings = settings;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failed = true;
            throw ex;
        } finally {
            if (enabled()) {
                Long userId = userId(SecurityContextHolder.getContext().getAuthentication());
                securityLog.request(userId, userId == null ? null : username(userId), request.getMethod(), request.getRequestURI(),
                        request.getQueryString(), failed ? 500 : response.getStatus(),
                        (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000),
                        ClientIp.of(request), ClientIp.userAgent(request));
            }
        }
    }

    private boolean enabled() {
        if (checkedAt.plus(Duration.ofSeconds(30)).isBefore(Instant.now())) {
            enabled = settings.isOn(SettingsService.REQUEST_LOG_ENABLED);
            checkedAt = Instant.now();
        }
        return enabled;
    }

    private static Long userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String username(Long id) {
        return usernames.computeIfAbsent(id, key -> users.findById(key).map(u -> u.getUsername()).orElse(null));
    }
}
