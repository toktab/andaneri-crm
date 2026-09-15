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
 * A line typed in a hurry, before it belongs anywhere: "myata - call after 3, ask if the bartender
 * tasted". Private to its author. It can be attached to a business, given a reminder time, or
 * ticked off.
 */
@Entity
@Table(name = "quick_notes")
public class QuickNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    private Business business;

    private Instant remindAt;

    /** When the push reminder went out; cleared when the reminder time changes. */
    private Instant remindedAt;

    private boolean done;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Business getBusiness() { return business; }
    public void setBusiness(Business business) { this.business = business; }
    public Instant getRemindAt() { return remindAt; }
    public void setRemindAt(Instant remindAt) { this.remindAt = remindAt; }
    public Instant getRemindedAt() { return remindedAt; }
    public void setRemindedAt(Instant remindedAt) { this.remindedAt = remindedAt; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
