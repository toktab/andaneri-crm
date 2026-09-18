package ge.andaneri.crm.notify;

import ge.andaneri.crm.domain.PushSubscription;
import ge.andaneri.crm.domain.PushSubscriptionRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Sends a notification to every device a person has switched notifications on for. */
@Service
public class PushService {

    /** What a notification says, and which screen tapping it opens. */
    public record Message(String title, String body, String url, String tag) {
    }

    /**
     * The push services of Chrome / Android, Firefox, Safari / iPhone and Edge. Devices only ever hand out
     * addresses there, so anything else is refused rather than letting the server be pointed at other hosts.
     */
    private static final Set<String> PUSH_HOSTS = Set.of("fcm.googleapis.com", "android.googleapis.com",
            "push.services.mozilla.com", "push.apple.com", "notify.windows.com");

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final PushSubscriptionRepository subscriptions;
    private final VapidKeys keys;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PushService(PushSubscriptionRepository subscriptions, VapidKeys keys, JsonMapper json) {
        this.subscriptions = subscriptions;
        this.keys = keys;
        this.json = json;
    }

    public static boolean isPushServiceAddress(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null
                    && PUSH_HOSTS.stream().anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** How one device answered: the push service's own status and words when it refused. */
    public record Delivery(Long deviceId, String device, boolean accepted, Integer status, String error) {
    }

    /** Sends to all of a person's devices, each in its own language. Returns how many accepted it. */
    public int sendToUser(Long userId, Function<String, Message> messageForLanguage) {
        return (int) sendToEachDevice(userId, messageForLanguage).stream().filter(Delivery::accepted).count();
    }

    /** The same send, with what each device answered - what the notification settings screen reports. */
    public List<Delivery> sendToEachDevice(Long userId, Function<String, Message> messageForLanguage) {
        List<Delivery> out = new ArrayList<>();
        for (PushSubscription subscription : subscriptions.findByUserId(userId)) {
            Long id = subscription.getId();
            String device = deviceName(subscription.getUserAgent());
            boolean ok = send(subscription, messageForLanguage.apply(subscription.getLang()));
            // Re-read: send() writes down what the push service said, or removes the device for good.
            PushSubscription after = subscriptions.findById(id).orElse(null);
            out.add(new Delivery(id, device, ok, after == null ? 410 : after.getLastStatus(),
                    after == null ? "gone" : after.getLastError()));
        }
        return out;
    }

    /** "Android · Chrome", "iPhone · Safari": enough to tell one of your own devices from another. */
    public static String deviceName(String userAgent) {
        String agent = userAgent == null ? "" : userAgent;
        String system = agent.contains("Android") ? "Android"
                : agent.contains("iPhone") ? "iPhone"
                : agent.contains("iPad") ? "iPad"
                : agent.contains("Mac OS") ? "Mac"
                : agent.contains("Windows") ? "Windows"
                : agent.contains("Linux") ? "Linux" : "?";
        String browser = agent.contains("Edg/") ? "Edge"
                : agent.contains("OPR/") ? "Opera"
                : agent.contains("Chrome/") ? "Chrome"
                : agent.contains("Firefox/") ? "Firefox"
                : agent.contains("Safari/") ? "Safari" : "?";
        return system + " · " + browser;
    }

    boolean send(PushSubscription subscription, Message message) {
        if (!isPushServiceAddress(subscription.getEndpoint())) {
            subscriptions.delete(subscription);
            return false;
        }
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("title", message.title());
            payload.put("body", message.body());
            payload.put("url", message.url());
            payload.put("tag", message.tag());
            byte[] body = WebPush.encrypt(json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8),
                    subscription.getP256dh(), subscription.getAuthSecret());
            HttpRequest request = HttpRequest.newBuilder(URI.create(subscription.getEndpoint()))
                    .timeout(Duration.ofSeconds(15))
                    .header("TTL", "3600")
                    .header("Urgency", "high")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", WebPush.vapidAuthorization(subscription.getEndpoint(), keys.pair(), keys.subject(), Instant.now()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                subscription.setLastStatus(status);
                subscription.setLastError(null);
                subscription.setLastTriedAt(Instant.now());
                subscriptions.save(subscription);
                subscriptions.markDelivered(subscription.getId(), Instant.now());
                return true;
            }
            if (status == 404 || status == 410) {
                // The device unsubscribed or the browser data was cleared: this address is gone for good.
                subscriptions.delete(subscription);
            } else {
                // Google and Apple both explain themselves in the body; keep it, the screen shows it.
                log.warn("Push to device {} refused with HTTP {}: {}", subscription.getId(), status, oneLine(response.body()));
                subscription.setLastStatus(status);
                subscription.setLastError(oneLine(response.body()));
                subscription.setLastTriedAt(Instant.now());
                subscriptions.save(subscription);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("Push to device {} failed: {}", subscription.getId(), ex.toString());
            subscription.setLastStatus(null);
            subscription.setLastError(oneLine(ex.toString()));
            subscription.setLastTriedAt(Instant.now());
            subscriptions.save(subscription);
        }
        return false;
    }

    private static String oneLine(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= 200 ? flat : flat.substring(0, 199) + "…";
    }
}
