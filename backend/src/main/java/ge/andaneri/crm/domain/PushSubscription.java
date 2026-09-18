package ge.andaneri.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One browser or installed web app that agreed to receive notifications: where to send them (the push
 * service address) and the keys the message is encrypted with, so only that device can read it.
 */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 1000)
    private String endpoint;

    @Column(nullable = false, length = 64)
    private String endpointHash;

    @Column(nullable = false)
    private String p256dh;

    @Column(name = "auth_secret", nullable = false)
    private String authSecret;

    /** The language the device's CRM was in, so the reminder reads the same. */
    private String lang;

    private String userAgent;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant lastSuccessAt;

    /** What the push service answered last time, so a silent phone can say why. */
    private Integer lastStatus;

    @Column(length = 200)
    private String lastError;

    private Instant lastTriedAt;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getEndpointHash() { return endpointHash; }
    public void setEndpointHash(String endpointHash) { this.endpointHash = endpointHash; }
    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }
    public String getAuthSecret() { return authSecret; }
    public void setAuthSecret(String authSecret) { this.authSecret = authSecret; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public Integer getLastStatus() { return lastStatus; }
    public void setLastStatus(Integer lastStatus) { this.lastStatus = lastStatus; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getLastTriedAt() { return lastTriedAt; }
    public void setLastTriedAt(Instant lastTriedAt) { this.lastTriedAt = lastTriedAt; }
}
