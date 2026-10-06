package ge.andaneri.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Andaneri ({@code own}) or a competitor. Salespeople can add one on the spot while logging a visit. */
@Entity
@Table(name = "brands")
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private boolean own;

    private boolean active = true;

    /**
     * Set here rather than only in the constructor below: a restore builds a brand with the no-arg
     * constructor and setters, and a brand the install has never seen used to fail to save at all -
     * which stopped a whole backup from going back in.
     */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Brand() {
    }

    public Brand(String name, boolean own) {
        this.name = name;
        this.own = own;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isOwn() { return own; }
    public void setOwn(boolean own) { this.own = own; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
