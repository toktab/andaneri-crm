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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A bar, cafe, restaurant or hotel we sell to, or want to. What replaced one row of the old
 * spreadsheet: everything that happened with it lives in its own tables (activities, tasks,
 * usages, purchases...) and points back here.
 */
@Entity
@Table(name = "businesses")
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private BusinessType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessStatus status = BusinessStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.NORMAL;

    private String address;
    private String city;
    private String district;
    private String phone;
    private String email;
    private String website;
    private String mapsUrl;
    private BigDecimal latitude;
    private BigDecimal longitude;
    /** The official identification code (საიდენტიფიკაციო კოდი). */
    private String idCode;

    /** The registered company behind the sign: "შპს ყავის ლაბორატორია". */
    private String legalName;

    /** How many locations they have; a chain of 27 is a different conversation from one cafe. */
    private Integer branches;

    /** When to find the right person there: "after 8 pm", "from 7". */
    private String visitHours;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** "Changes the cocktail menu every week" - free text, it varies too much to structure. */
    private String menuChange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Openness switchOpenness = Openness.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriceSensitivity priceSensitivity = PriceSensitivity.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Satisfaction satisfaction = Satisfaction.UNKNOWN;

    /** Why they use their current brand, what worries them about switching. */
    @Column(columnDefinition = "TEXT")
    private String competitorNotes;

    /** Days between orders when known; otherwise worked out from the purchases, or the default setting. */
    private Integer reorderDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    /** The sheet (workbook tab) it is filed under, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sheet_id")
    private BusinessSheet sheet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    private Instant lastContactAt;
    private LocalDate lastPurchaseDate;
    private int purchaseCount;

    private boolean archived;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @ElementCollection
    @CollectionTable(name = "business_drink_types", joinColumns = @JoinColumn(name = "business_id"))
    @Column(name = "drink_type")
    @Enumerated(EnumType.STRING)
    private Set<DrinkType> drinkTypes = new LinkedHashSet<>();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BusinessType getType() { return type; }
    public void setType(BusinessType type) { this.type = type; }
    public BusinessStatus getStatus() { return status; }
    public void setStatus(BusinessStatus status) { this.status = status; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getMapsUrl() { return mapsUrl; }
    public void setMapsUrl(String mapsUrl) { this.mapsUrl = mapsUrl; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public String getIdCode() { return idCode; }
    public void setIdCode(String idCode) { this.idCode = idCode; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public Integer getBranches() { return branches; }
    public void setBranches(Integer branches) { this.branches = branches; }
    public String getVisitHours() { return visitHours; }
    public void setVisitHours(String visitHours) { this.visitHours = visitHours; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getMenuChange() { return menuChange; }
    public void setMenuChange(String menuChange) { this.menuChange = menuChange; }
    public Openness getSwitchOpenness() { return switchOpenness; }
    public void setSwitchOpenness(Openness switchOpenness) { this.switchOpenness = switchOpenness; }
    public PriceSensitivity getPriceSensitivity() { return priceSensitivity; }
    public void setPriceSensitivity(PriceSensitivity priceSensitivity) { this.priceSensitivity = priceSensitivity; }
    public Satisfaction getSatisfaction() { return satisfaction; }
    public void setSatisfaction(Satisfaction satisfaction) { this.satisfaction = satisfaction; }
    public String getCompetitorNotes() { return competitorNotes; }
    public void setCompetitorNotes(String competitorNotes) { this.competitorNotes = competitorNotes; }
    public Integer getReorderDays() { return reorderDays; }
    public void setReorderDays(Integer reorderDays) { this.reorderDays = reorderDays; }
    public User getAssignedTo() { return assignedTo; }
    public void setAssignedTo(User assignedTo) { this.assignedTo = assignedTo; }
    public BusinessSheet getSheet() { return sheet; }
    public void setSheet(BusinessSheet sheet) { this.sheet = sheet; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getLastContactAt() { return lastContactAt; }
    public void setLastContactAt(Instant lastContactAt) { this.lastContactAt = lastContactAt; }
    public LocalDate getLastPurchaseDate() { return lastPurchaseDate; }
    public void setLastPurchaseDate(LocalDate lastPurchaseDate) { this.lastPurchaseDate = lastPurchaseDate; }
    public int getPurchaseCount() { return purchaseCount; }
    public void setPurchaseCount(int purchaseCount) { this.purchaseCount = purchaseCount; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public Set<DrinkType> getDrinkTypes() { return drinkTypes; }
}
