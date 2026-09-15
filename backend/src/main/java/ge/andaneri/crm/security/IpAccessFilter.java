package ge.andaneri.crm.security;

import ge.andaneri.crm.config.CrmProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns away blocked addresses, and while the whitelist is on, every address not on it. The machine
 * itself (127.0.0.1, ::1) is never turned away. Root keeps access from anywhere unless
 * ROOT_BYPASS_WHITELIST is false, so switching the whitelist on can never lock the last admin out.
 */
public class IpAccessFilter extends OncePerRequestFilter {

    private final IpRules rules;
    private final CrmProperties properties;
    private final JwtDecoder jwtDecoder;

    public IpAccessFilter(IpRules rules, CrmProperties properties, JwtDecoder jwtDecoder) {
        this.rules = rules;
        this.properties = properties;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = ClientIp.of(request);
        if (!ClientIp.isLoopback(ip)) {
            if (rules.blocked(ip)) {
                deny(response, "IP_BLOCKED");
                return;
            }
            // The sign-in itself is let through: AuthController refuses everyone but root there.
            if (rules.whitelistOn() && !rules.allowed(ip) && !request.getRequestURI().equals("/api/auth/login") && !isRoot(request)) {
                deny(response, "IP_NOT_ALLOWED");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isRoot(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!properties.rootBypassWhitelist() || header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));
            List<String> roles = jwt.getClaimAsStringList("roles");
            return roles != null && roles.contains("ROOT");
        } catch (JwtException ex) {
            return false;
        }
    }

    private static void deny(HttpServletResponse response, String code) throws IOException {
        response.setStatus(403);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":403,\"code\":\"" + code + "\"}");
    }
}
