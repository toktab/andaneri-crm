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
 * An address or range (single IP or CIDR such as 185.12.0.0/16). ALLOW rules are the whitelist, applied
 * only while the whitelist is switched on; BLOCK rules always apply, and the automatic ones expire.
 */
@Entity
@Table(name = "ip_rules")
public class IpRule {

    public enum Kind { ALLOW, BLOCK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    private String note;

    /** Added by the system after too many failed sign-ins, not by a person. */
    private boolean automatic;

    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isLive(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public Long getId() { return id; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isAutomatic() { return automatic; }
    public void setAutomatic(boolean automatic) { this.automatic = automatic; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
