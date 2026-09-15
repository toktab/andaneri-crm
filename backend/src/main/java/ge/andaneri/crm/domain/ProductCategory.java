package ge.andaneri.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Syrup, fruit puree, coffee cream, sauce... Every one gets its own "do they use it?" question. */
@Entity
@Table(name = "product_categories")
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nameKa;

    @Column(nullable = false)
    private String nameEn;

    private int sortOrder;

    private boolean active = true;

    public ProductCategory() {
    }

    public ProductCategory(String nameKa, String nameEn, int sortOrder) {
        this.nameKa = nameKa;
        this.nameEn = nameEn;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getNameKa() { return nameKa; }
    public void setNameKa(String nameKa) { this.nameKa = nameKa; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
