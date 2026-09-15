package ge.andaneri.crm.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A product on a price list. Ours carry the price from the Andaneri sheet and the menu section
 * ("Signature Syrups", "Sugar Free"...); competitors' products are optional, since a usage row
 * can name a brand and flavor without one.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    private String sectionKa;
    private String sectionEn;

    @Column(nullable = false)
    private String nameKa;

    @Column(nullable = false)
    private String nameEn;

    private String packSize;
    private String unit;
    private BigDecimal price;
    private Integer juicePercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String description;

    private int sortOrder;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToMany
    @JoinTable(name = "product_flavors",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "flavor_id"))
    private Set<Flavor> flavors = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "product_applications", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "application")
    @Enumerated(EnumType.STRING)
    private Set<DrinkType> applications = new LinkedHashSet<>();

    public Long getId() { return id; }
    public Brand getBrand() { return brand; }
    public void setBrand(Brand brand) { this.brand = brand; }
    public ProductCategory getCategory() { return category; }
    public void setCategory(ProductCategory category) { this.category = category; }
    public String getSectionKa() { return sectionKa; }
    public void setSectionKa(String sectionKa) { this.sectionKa = sectionKa; }
    public String getSectionEn() { return sectionEn; }
    public void setSectionEn(String sectionEn) { this.sectionEn = sectionEn; }
    public String getNameKa() { return nameKa; }
    public void setNameKa(String nameKa) { this.nameKa = nameKa; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getPackSize() { return packSize; }
    public void setPackSize(String packSize) { this.packSize = packSize; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getJuicePercent() { return juicePercent; }
    public void setJuicePercent(Integer juicePercent) { this.juicePercent = juicePercent; }
    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<Flavor> getFlavors() { return flavors; }
    public Set<DrinkType> getApplications() { return applications; }
}
