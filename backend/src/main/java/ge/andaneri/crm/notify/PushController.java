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
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    public record PushStatus(String publicKey, long devices, int reminderMinutes, List<DeviceInfo> deviceList) {

        static PushStatus of(String publicKey, List<PushSubscription> devices, int reminderMinutes, String here) {
            return new PushStatus(publicKey, devices.size(), reminderMinutes,
                    devices.stream().map(d -> DeviceInfo.of(d, here)).toList());
        }
    }

    /** One device of this person's, as the notification screen lists it. */
    public record DeviceInfo(Long id, String name, boolean thisDevice, Instant addedAt, Instant lastSuccessAt,
            Integer lastStatus, String lastError) {

        static DeviceInfo of(PushSubscription s, String here) {
            return new DeviceInfo(s.getId(), PushService.deviceName(s.getUserAgent()),
                    here != null && here.equals(s.getEndpointHash()), s.getCreatedAt(), s.getLastSuccessAt(),
                    s.getLastStatus(), s.getLastError());
        }
    }

    public record TestResult(int sent, List<PushService.Delivery> devices) {
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
    public PushStatus status(@RequestParam(required = false) String endpoint) {
        User user = currentUser.require();
        return status(user, endpoint);
    }

    private PushStatus status(User user, String endpoint) {
        return PushStatus.of(keys.publicKey(), subscriptions.findByUserId(user.getId()), user.getReminderMinutes(),
                endpoint == null || endpoint.isBlank() ? null : hash(endpoint));
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
        return status(user, request.endpoint());
    }

    @PostMapping("/push/subscriptions/remove")
    public PushStatus unsubscribe(@Valid @RequestBody EndpointRequest request) {
        User user = currentUser.require();
        subscriptions.findByEndpointHash(hash(request.endpoint()))
                .filter(s -> s.getUser().getId().equals(user.getId()))
                .ifPresent(subscriptions::delete);
        return status(user, null);
    }

    /** Sends a test to every device and reports what each one answered, refusals included. */
    @PostMapping("/push/test")
    public TestResult test() {
        User user = currentUser.require();
        List<PushService.Delivery> devices = push.sendToEachDevice(user.getId(), ReminderText::test);
        return new TestResult((int) devices.stream().filter(PushService.Delivery::accepted).count(), devices);
    }

    @PutMapping("/me/reminders")
    public PushStatus reminders(@Valid @RequestBody ReminderSettings request) {
        User user = users.findById(currentUser.require().getId()).orElseThrow(ApiException::notFound);
        user.setReminderMinutes(request.reminderMinutes());
        users.save(user);
        return status(user, null);
    }

    static String hash(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
