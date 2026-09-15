package ge.andaneri.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One flavor list shared by our products, competitors' products and interests. That is what lets
 * "they use Monin Mango" point straight at our Mango and our Dragon Fruit and Mango.
 */
@Entity
@Table(name = "flavors")
public class Flavor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nameKa;

    @Column(nullable = false)
    private String nameEn;

    private boolean active = true;

    public Flavor() {
    }

    public Flavor(String nameKa, String nameEn) {
        this.nameKa = nameKa;
        this.nameEn = nameEn;
    }

    public Long getId() { return id; }
    public String getNameKa() { return nameKa; }
    public void setNameKa(String nameKa) { this.nameKa = nameKa; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
