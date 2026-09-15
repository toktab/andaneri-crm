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

    /** Sends to all of a person's devices, each in its own language. Returns how many accepted it. */
    public int sendToUser(Long userId, Function<String, Message> messageForLanguage) {
        int delivered = 0;
        for (PushSubscription subscription : subscriptions.findByUserId(userId)) {
            if (send(subscription, messageForLanguage.apply(subscription.getLang()))) {
                delivered++;
            }
        }
        return delivered;
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
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                subscriptions.markDelivered(subscription.getId(), Instant.now());
                return true;
            }
            if (status == 404 || status == 410) {
                // The device unsubscribed or the browser data was cleared: this address is gone for good.
                subscriptions.delete(subscription);
            } else {
                log.warn("Push to device {} refused with HTTP {}", subscription.getId(), status);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("Push to device {} failed: {}", subscription.getId(), ex.toString());
        }
        return false;
    }
}
