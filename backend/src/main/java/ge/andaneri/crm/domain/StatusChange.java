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

/** "14 Sep - Toko moved it from Contacted to Meeting". Also what conversion reports count. */
@Entity
@Table(name = "status_changes")
public class StatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id")
    private Business business;

    @Enumerated(EnumType.STRING)
    private BusinessStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Instant changedAt = Instant.now();

    private String note;

    public StatusChange() {
    }

    public StatusChange(Business business, BusinessStatus fromStatus, BusinessStatus toStatus, User user, String note) {
        this.business = business;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.user = user;
        this.note = note;
    }

    public Long getId() { return id; }
    public Business getBusiness() { return business; }
    public BusinessStatus getFromStatus() { return fromStatus; }
    public BusinessStatus getToStatus() { return toStatus; }
    public User getUser() { return user; }
    public Instant getChangedAt() { return changedAt; }

    /** Set when restoring a backup, so the history keeps the time the step actually happened. */
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
    public String getNote() { return note; }
}
