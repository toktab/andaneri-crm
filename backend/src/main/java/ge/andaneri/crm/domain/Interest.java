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
 * Something a business would like: a flavor ("Mango"), or one of our products. Kept after it is
 * bought or turned down, with the status saying so.
 */
@Entity
@Table(name = "interests")
public class Interest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flavor_id")
    private Flavor flavor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterestStatus status = InterestStatus.INTERESTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterestReason reason = InterestReason.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TastingFeedback feedback = TastingFeedback.UNKNOWN;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Business getBusiness() { return business; }
    public void setBusiness(Business business) { this.business = business; }
    public Flavor getFlavor() { return flavor; }
    public void setFlavor(Flavor flavor) { this.flavor = flavor; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public InterestStatus getStatus() { return status; }
    public void setStatus(InterestStatus status) { this.status = status; }
    public InterestReason getReason() { return reason; }
    public void setReason(InterestReason reason) { this.reason = reason; }
    public TastingFeedback getFeedback() { return feedback; }
    public void setFeedback(TastingFeedback feedback) { this.feedback = feedback; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
