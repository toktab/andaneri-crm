package ge.andaneri.crm.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * An error the client can act on. The {@code code} is stable and machine-readable: the frontend
 * translates it into Georgian or English, so the server never has to produce messages.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> properties;

    public ApiException(HttpStatus status, String code) {
        this(status, code, Map.of());
    }

    public ApiException(HttpStatus status, String code, Map<String, Object> properties) {
        // Expected outcomes, not bugs: no stack trace to fill in.
        super(code, null, false, false);
        this.status = status;
        this.code = code;
        this.properties = Map.copyOf(properties);
    }

    public static ApiException badRequest(String code) {
        return new ApiException(HttpStatus.BAD_REQUEST, code);
    }

    public static ApiException unauthorized(String code) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code);
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public static ApiException conflict(String code) {
        return new ApiException(HttpStatus.CONFLICT, code);
    }

    /** A failure tied to one request field, reported in the same shape as bean validation errors. */
    public static ApiException field(String field, String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", Map.of("fields", Map.of(field, reason)));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
