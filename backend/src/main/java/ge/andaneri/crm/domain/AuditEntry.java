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
 * Who created, changed or removed what, from which address, so nothing disappears silently when several
 * people share accounts. Rows come from two places: short readable summaries written by the services
 * ("STATUS: NEW -> CONTACTED"), and one row per database insert, update or delete with before / after
 * values in {@code changes}, written by {@code ge.andaneri.crm.security.ChangeAuditListener}.
 */
@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long businessId;

    @Column(nullable = false)
    private String entity;

    private Long entityId;

    @Column(nullable = false)
    private String action;

    private String summary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String ip;

    /** JSON: {"field": ["before", "after"]}. */
    @Column(columnDefinition = "TEXT")
    private String changes;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public AuditEntry() {
    }

    public AuditEntry(Long businessId, String entity, Long entityId, String action, String summary, User user) {
        this.businessId = businessId;
        this.entity = entity;
        this.entityId = entityId;
        this.action = action;
        this.summary = summary == null || summary.length() <= 500 ? summary : summary.substring(0, 497) + "...";
        this.user = user;
    }

    public Long getId() { return id; }
    public Long getBusinessId() { return businessId; }
    public String getEntity() { return entity; }
    public Long getEntityId() { return entityId; }
    public String getAction() { return action; }
    public String getSummary() { return summary; }
    public User getUser() { return user; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getChanges() { return changes; }
    public Instant getCreatedAt() { return createdAt; }
}
