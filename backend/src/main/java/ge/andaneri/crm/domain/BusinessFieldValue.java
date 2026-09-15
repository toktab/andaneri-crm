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

/** One business's value for one custom field. */
@Entity
@Table(name = "business_field_values")
public class BusinessFieldValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id")
    private CustomField field;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String value;

    public BusinessFieldValue() {
    }

    public BusinessFieldValue(Business business, CustomField field, String value) {
        this.business = business;
        this.field = field;
        this.value = value;
    }

    public Long getId() { return id; }
    public Business getBusiness() { return business; }
    public CustomField getField() { return field; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
