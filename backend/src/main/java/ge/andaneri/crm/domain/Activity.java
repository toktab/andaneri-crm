package ge.andaneri.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Something that happened: "14 Sep, called, no answer". Replaces the dated lines typed into the
 * comments cell of the old spreadsheet. Never edited into something else, only added to.
 */
@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityResult result;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** From the old spreadsheet: the notes are the original cell, the type, result and date are best guesses. */
    private boolean imported;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Business getBusiness() { return business; }
    public void setBusiness(Business business) { this.business = business; }
    public Contact getContact() { return contact; }
    public void setContact(Contact contact) { this.contact = contact; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public ActivityType getType() { return type; }
    public void setType(ActivityType type) { this.type = type; }
    public ActivityResult getResult() { return result; }
    public void setResult(ActivityResult result) { this.result = result; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isImported() { return imported; }
    public void setImported(boolean imported) { this.imported = imported; }
    public Instant getCreatedAt() { return createdAt; }
}
