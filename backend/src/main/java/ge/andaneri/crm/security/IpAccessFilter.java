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
 * itself (127.0.0.1, ::1) is never turned away, and neither is signing in: a blocked address must still
 * be able to reach the sign-in, where the key accounts are let through. Root and the first admin keep
 * access from anywhere, so a block can never shut out the person who can lift it.
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
            boolean signingIn = request.getRequestURI().equals("/api/auth/login");
            if (rules.blocked(ip) && !signingIn && !hasRole(request, "ADMIN", "ROOT")) {
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
        return properties.rootBypassWhitelist() && hasRole(request, "ROOT");
    }

    /** Whether the request carries a good token for one of these roles. */
    private boolean hasRole(HttpServletRequest request, String... wanted) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return false;
            }
            for (String role : wanted) {
                if (roles.contains(role)) {
                    return true;
                }
            }
            return false;
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
