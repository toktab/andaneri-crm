package ge.andaneri.crm.notify;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.domain.PushSubscription;
import ge.andaneri.crm.domain.PushSubscriptionRepository;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Switching notifications on and off per device, a test message, and each person's default reminder time. */
@RestController
@RequestMapping("/api")
public class PushController {

    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
    }

    public record SubscribeRequest(@NotBlank @Size(max = 1000) String endpoint, @NotNull @Valid Keys keys, @Size(max = 5) String lang) {
    }

    public record EndpointRequest(@NotBlank @Size(max = 1000) String endpoint) {
    }

    public record ReminderSettings(@NotNull @Min(0) @Max(10080) Integer reminderMinutes) {
    }

    public record PushStatus(String publicKey, long devices, int reminderMinutes) {
    }

    public record TestResult(int sent) {
    }

    private final PushSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final PushService push;
    private final VapidKeys keys;
    private final CurrentUser currentUser;

    public PushController(PushSubscriptionRepository subscriptions, UserRepository users, PushService push, VapidKeys keys,
            CurrentUser currentUser) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.push = push;
        this.keys = keys;
        this.currentUser = currentUser;
    }

    @GetMapping("/push/status")
    public PushStatus status() {
        User user = currentUser.require();
        return new PushStatus(keys.publicKey(), subscriptions.countByUserId(user.getId()), user.getReminderMinutes());
    }

    /** This device wants notifications. The same browser signed in as someone else moves over to them. */
    @PostMapping("/push/subscriptions")
    public PushStatus subscribe(@Valid @RequestBody SubscribeRequest request, HttpServletRequest http) {
        User user = currentUser.require();
        if (!PushService.isPushServiceAddress(request.endpoint())) {
            throw ApiException.field("endpoint", "invalid");
        }
        PushSubscription subscription = subscriptions.findByEndpointHash(hash(request.endpoint())).orElseGet(PushSubscription::new);
        subscription.setUser(user);
        subscription.setEndpoint(request.endpoint());
        subscription.setEndpointHash(hash(request.endpoint()));
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuthSecret(request.keys().auth());
        subscription.setLang("en".equals(request.lang()) ? "en" : "ka");
        subscription.setUserAgent(ClientIp.userAgent(http));
        subscriptions.save(subscription);
        return new PushStatus(keys.publicKey(), subscriptions.countByUserId(user.getId()), user.getReminderMinutes());
    }

    @PostMapping("/push/subscriptions/remove")
    public PushStatus unsubscribe(@Valid @RequestBody EndpointRequest request) {
        User user = currentUser.require();
        subscriptions.findByEndpointHash(hash(request.endpoint()))
                .filter(s -> s.getUser().getId().equals(user.getId()))
                .ifPresent(subscriptions::delete);
        return new PushStatus(keys.publicKey(), subscriptions.countByUserId(user.getId()), user.getReminderMinutes());
    }

    @PostMapping("/push/test")
    public TestResult test() {
        User user = currentUser.require();
        return new TestResult(push.sendToUser(user.getId(), ReminderText::test));
    }

    @PutMapping("/me/reminders")
    public PushStatus reminders(@Valid @RequestBody ReminderSettings request) {
        User user = users.findById(currentUser.require().getId()).orElseThrow(ApiException::notFound);
        user.setReminderMinutes(request.reminderMinutes());
        users.save(user);
        return new PushStatus(keys.publicKey(), subscriptions.countByUserId(user.getId()), user.getReminderMinutes());
    }

    static String hash(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
