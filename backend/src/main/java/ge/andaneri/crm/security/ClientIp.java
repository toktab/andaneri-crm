package ge.andaneri.crm.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The visitor's address. Directly on the internet this is the connection's own address. Behind a reverse
 * proxy it is either taken from X-Forwarded-For (FORWARD_HEADERS=native, see application.properties) or,
 * where the platform's proxy puts the address in a header of its own (Railway: X-Real-IP), from that header
 * (CLIENT_IP_HEADER). Never set either without such a proxy in front: anyone can send those headers.
 */
public final class ClientIp {

    private static volatile String header;

    private ClientIp() {
    }

    /** Wires CLIENT_IP_HEADER in at startup. */
    @Component
    static class Settings {
        Settings(@Value("${crm.client-ip-header:}") String name) {
            useHeader(name);
        }
    }

    static void useHeader(String name) {
        header = name == null || name.isBlank() ? null : name.trim();
    }

    public static String of(HttpServletRequest request) {
        String ip = null;
        if (header != null) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                ip = value.split(",")[0].trim();
            }
            // Loopback is let past every IP rule, so it is never believed from a header, only from the socket.
            if (isLoopback(normalize(ip))) {
                ip = null;
            }
        }
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        return normalize(ip);
    }

    private static String normalize(String ip) {
        return "0:0:0:0:0:0:0:1".equals(ip) ? "::1" : ip;
    }

    /** The address of the request being handled on this thread, or null outside a request. */
    public static String current() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? of(attributes.getRequest()) : null;
    }

    public static boolean isLoopback(String ip) {
        return ip != null && (ip.startsWith("127.") || "::1".equals(ip));
    }

    public static String userAgent(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        return agent == null ? null : agent.length() > 300 ? agent.substring(0, 300) : agent;
    }
}
