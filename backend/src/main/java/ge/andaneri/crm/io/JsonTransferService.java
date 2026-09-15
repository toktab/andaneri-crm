package ge.andaneri.crm.io;

import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.common.Text;
import ge.andaneri.crm.domain.Activity;
import ge.andaneri.crm.domain.ActivityRepository;
import ge.andaneri.crm.domain.ActivityResult;
import ge.andaneri.crm.domain.ActivityType;
import ge.andaneri.crm.domain.Brand;
import ge.andaneri.crm.domain.BrandRepository;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.BusinessTypeRepository;
import ge.andaneri.crm.domain.CategoryUsageRepository;
import ge.andaneri.crm.domain.Comment;
import ge.andaneri.crm.domain.CommentRepository;
import ge.andaneri.crm.domain.Contact;
import ge.andaneri.crm.domain.ContactChannel;
import ge.andaneri.crm.domain.ContactRepository;
import ge.andaneri.crm.domain.DrinkType;
import ge.andaneri.crm.domain.Flavor;
import ge.andaneri.crm.domain.FlavorRepository;
import ge.andaneri.crm.domain.Interest;
import ge.andaneri.crm.domain.InterestReason;
import ge.andaneri.crm.domain.InterestRepository;
import ge.andaneri.crm.domain.InterestStatus;
import ge.andaneri.crm.domain.Openness;
import ge.andaneri.crm.domain.PriceSensitivity;
import ge.andaneri.crm.domain.Priority;
import ge.andaneri.crm.domain.Product;
import ge.andaneri.crm.domain.ProductCategory;
import ge.andaneri.crm.domain.ProductCategoryRepository;
import ge.andaneri.crm.domain.ProductRepository;
import ge.andaneri.crm.domain.ProductUsage;
import ge.andaneri.crm.domain.ProductUsageRepository;
import ge.andaneri.crm.domain.Purchase;
import ge.andaneri.crm.domain.PurchaseItem;
import ge.andaneri.crm.domain.PurchaseRepository;
import ge.andaneri.crm.domain.Satisfaction;
import ge.andaneri.crm.domain.StatusChange;
import ge.andaneri.crm.domain.StatusChangeRepository;
import ge.andaneri.crm.domain.Task;
import ge.andaneri.crm.domain.TaskRepository;
import ge.andaneri.crm.domain.TaskStatus;
import ge.andaneri.crm.domain.TaskType;
import ge.andaneri.crm.domain.TastingFeedback;
import ge.andaneri.crm.domain.UsageAnswer;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.service.AuditService;
import ge.andaneri.crm.service.BusinessService;
import ge.andaneri.crm.service.DuplicateIndex;
import ge.andaneri.crm.web.BusinessDtos.DuplicateDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The CRM's own file format: every business with everything that belongs to it, as readable JSON.
 * For backups, moving data between installs, or handing a colleague a list with its history.
 * Catalog things (types, categories, brands, flavors, products) and people are referred to by
 * name, so a file travels between databases whose ids differ.
 */
@Service
public class JsonTransferService {

    public static final String FORMAT = "andaneri-crm";
    public static final int VERSION = 1;

    public record JsonFile(String format, Integer version, Instant exportedAt, List<JsonBusiness> businesses) {
    }

    public record JsonBusiness(String name, String legalName, String type, String status, String priority,
            String address, String city, String district, String phone, String email, String website, String mapsUrl,
            String idCode, Integer branches, String visitHours, String notes, String menuChange, String switchOpenness,
            String priceSensitivity, String satisfaction, String competitorNotes, Integer reorderDays, String assignedTo,
            List<String> drinkTypes, Instant createdAt, List<JsonContact> contacts, List<JsonAnswer> categoryAnswers,
            List<JsonUsage> usages, List<JsonInterest> interests, List<JsonActivity> activities, List<JsonTask> tasks,
            List<JsonComment> comments, List<JsonPurchase> purchases,
            /** Where it is filed: project and sheet names. */
            String project, String sheet,
            /** Custom field label to value. */
            Map<String, String> customFields) {
    }

    public record JsonContact(String name, String roleTitle, String phone, String email, String preferredChannel,
            Boolean decisionMaker, String notes) {
    }

    public record JsonAnswer(String category, String answer, String notes) {
    }

    public record JsonUsage(String category, String brand, String flavor, String productName, String quantity,
            String frequency, String notes) {
    }

    public record JsonInterest(String flavor, String product, String status, String reason, String feedback, String notes) {
    }

    public record JsonActivity(String type, String result, Instant occurredAt, String notes, String user, String contact,
            Boolean imported) {
    }

    public record JsonTask(String type, String title, Instant dueAt, Instant endAt, Boolean allDay, String location,
            String priority, String status, String notes, String assignedTo) {
    }

    public record JsonComment(String body, String author, Instant createdAt) {
    }

    public record JsonPurchase(LocalDate purchaseDate, String notes, String user, List<JsonItem> items) {
    }

    public record JsonItem(String product, String description, BigDecimal quantity, BigDecimal unitPrice) {
    }

    public record JsonImportResult(boolean dryRun, int total, int created, int skippedDuplicates, int skippedInvalid,
            List<DuplicateDto> duplicates, int contacts, int activities, int tasks, int purchases) {
    }

    private final BusinessRepository businesses;
    private final BusinessTypeRepository types;
    private final ProductCategoryRepository categories;
    private final BrandRepository brands;
    private final FlavorRepository flavors;
    private final ProductRepository products;
    private final ContactRepository contacts;
    private final CategoryUsageRepository categoryUsages;
    private final ProductUsageRepository usages;
    private final InterestRepository interests;
    private final ActivityRepository activities;
    private final TaskRepository tasks;
    private final CommentRepository comments;
    private final PurchaseRepository purchases;
    private final StatusChangeRepository statusChanges;
    private final UserRepository users;
    private final BusinessService businessService;
    private final AuditService audit;
    private final ge.andaneri.crm.service.WorkspaceService workspace;
    private final ge.andaneri.crm.domain.BusinessFieldValueRepository fieldValues;

    private Map<String, String> customByLabel(Long businessId) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (var value : fieldValues.findByBusinessId(businessId)) {
            values.put(value.getField().getLabel(), value.getValue());
        }
        return values;
    }

    public JsonTransferService(BusinessRepository businesses, BusinessTypeRepository types,
            ProductCategoryRepository categories, BrandRepository brands, FlavorRepository flavors,
            ProductRepository products, ContactRepository contacts, CategoryUsageRepository categoryUsages,
            ProductUsageRepository usages, InterestRepository interests, ActivityRepository activities,
            TaskRepository tasks, CommentRepository comments, PurchaseRepository purchases,
            StatusChangeRepository statusChanges, UserRepository users, BusinessService businessService,
            AuditService audit, ge.andaneri.crm.service.WorkspaceService workspace,
            ge.andaneri.crm.domain.BusinessFieldValueRepository fieldValues) {
        this.workspace = workspace;
        this.fieldValues = fieldValues;
        this.businesses = businesses;
        this.types = types;
        this.categories = categories;
        this.brands = brands;
        this.flavors = flavors;
        this.products = products;
        this.contacts = contacts;
        this.categoryUsages = categoryUsages;
        this.usages = usages;
        this.interests = interests;
        this.activities = activities;
        this.tasks = tasks;
        this.comments = comments;
        this.purchases = purchases;
        this.statusChanges = statusChanges;
        this.users = users;
        this.businessService = businessService;
        this.audit = audit;
    }

    // ================================================================== export

    /** Call inside the transaction that loaded {@code rows}. */
    @Transactional(readOnly = true)
    public JsonFile export(List<Business> rows) {
        List<JsonBusiness> out = new ArrayList<>();
        for (Business b : rows) {
            Long id = b.getId();
            out.add(new JsonBusiness(b.getName(), b.getLegalName(), b.getType() == null ? null : b.getType().getNameEn(),
                    b.getStatus().name(), b.getPriority().name(), b.getAddress(), b.getCity(), b.getDistrict(), b.getPhone(),
                    b.getEmail(), b.getWebsite(), b.getMapsUrl(), b.getIdCode(), b.getBranches(), b.getVisitHours(), b.getNotes(),
                    b.getMenuChange(), b.getSwitchOpenness().name(), b.getPriceSensitivity().name(), b.getSatisfaction().name(),
                    b.getCompetitorNotes(), b.getReorderDays(), b.getAssignedTo() == null ? null : b.getAssignedTo().getUsername(),
                    b.getDrinkTypes().stream().map(Enum::name).sorted().toList(), b.getCreatedAt(),
                    contacts.findByBusinessIdOrderByDecisionMakerDescNameAsc(id).stream()
                            .map(c -> new JsonContact(c.getName(), c.getRoleTitle(), c.getPhone(), c.getEmail(),
                                    c.getPreferredChannel().name(), c.isDecisionMaker(), c.getNotes())).toList(),
                    categoryUsages.findByBusinessId(id).stream()
                            .map(a -> new JsonAnswer(a.getCategory().getNameEn(), a.getAnswer().name(), a.getNotes())).toList(),
                    usages.findForBusiness(id).stream()
                            .map(u -> new JsonUsage(u.getCategory().getNameEn(), u.getBrand() == null ? null : u.getBrand().getName(),
                                    u.getFlavor() == null ? null : u.getFlavor().getNameEn(), u.getProductName(), u.getQuantity(),
                                    u.getFrequency(), u.getNotes())).toList(),
                    interests.findForBusiness(id).stream()
                            .map(i -> new JsonInterest(i.getFlavor() == null ? null : i.getFlavor().getNameEn(),
                                    i.getProduct() == null ? null : i.getProduct().getNameEn(), i.getStatus().name(),
                                    i.getReason().name(), i.getFeedback().name(), i.getNotes())).toList(),
                    activities.findForBusiness(id).stream()
                            .sorted(Comparator.comparing(Activity::getOccurredAt))
                            .map(a -> new JsonActivity(a.getType().name(), a.getResult().name(), a.getOccurredAt(), a.getNotes(),
                                    a.getUser().getUsername(), a.getContact() == null ? null : a.getContact().getName(), a.isImported()))
                            .toList(),
                    tasks.findForBusiness(id).stream()
                            .map(t -> new JsonTask(t.getType().name(), t.getTitle(), t.getDueAt(), t.getEndAt(), t.isAllDay(),
                                    t.getLocation(), t.getPriority().name(), t.getStatus().name(), t.getNotes(),
                                    t.getAssignedTo().getUsername())).toList(),
                    comments.findForBusiness(id).stream()
                            .sorted(Comparator.comparing(Comment::getCreatedAt))
                            .map(c -> new JsonComment(c.getBody(), c.getAuthor().getUsername(), c.getCreatedAt())).toList(),
                    purchases.findForBusiness(id).stream()
                            .sorted(Comparator.comparing(Purchase::getPurchaseDate))
                            .map(p -> new JsonPurchase(p.getPurchaseDate(), p.getNotes(), p.getUser().getUsername(),
                                    p.getItems().stream().map(i -> new JsonItem(i.getProduct() == null ? null : i.getProduct().getNameEn(),
                                            i.getDescription(), i.getQuantity(), i.getUnitPrice())).toList()))
                            .toList(),
                    b.getSheet() == null || b.getSheet().getWorkbook() == null ? null : b.getSheet().getWorkbook().getName(),
                    b.getSheet() == null ? null : b.getSheet().getName(),
                    customByLabel(id)));
        }
        return new JsonFile(FORMAT, VERSION, Instant.now(), out);
    }

    // ================================================================== import

    /** With {@code dryRun} nothing is written: the counts say what an import would do. */
    @Transactional
    public JsonImportResult importFile(JsonFile file, boolean skipDuplicates, boolean dryRun, User user) {
        if (file == null || !FORMAT.equals(file.format()) || file.businesses() == null) {
            throw ApiException.badRequest("WRONG_FORMAT");
        }
        DuplicateIndex index = DuplicateIndex.of(businesses.findByArchivedFalse());
        Lookup lookup = new Lookup();
        Instant now = Instant.now();
        int created = 0;
        int skippedDuplicates = 0;
        int skippedInvalid = 0;
        int contactCount = 0;
        int activityCount = 0;
        int taskCount = 0;
        int purchaseCount = 0;
        List<DuplicateDto> duplicates = new ArrayList<>();

        for (JsonBusiness jb : file.businesses()) {
            if (jb == null || Text.blankToNull(jb.name()) == null) {
                skippedInvalid++;
                continue;
            }
            Optional<DuplicateDto> duplicate = index.match(jb.name(), jb.address(), jb.phone(), jb.idCode(), null);
            index.add(null, jb.name(), jb.address(), jb.phone(), jb.idCode());
            if (duplicate.isPresent()) {
                if (duplicates.size() < 50) {
                    duplicates.add(duplicate.get());
                }
                if (skipDuplicates && duplicate.get().strong()) {
                    skippedDuplicates++;
                    continue;
                }
            }
            created++;
            contactCount += size(jb.contacts());
            activityCount += size(jb.activities());
            taskCount += size(jb.tasks());
            purchaseCount += size(jb.purchases());
            if (!dryRun) {
                create(jb, user, lookup, now);
            }
        }
        if (!dryRun) {
            audit.record(null, "Import", null, "COMPLETED", "JSON: " + created + " created, " + skippedDuplicates + " duplicates skipped", user);
        }
        return new JsonImportResult(dryRun, file.businesses().size(), created, skippedDuplicates, skippedInvalid, duplicates,
                contactCount, activityCount, taskCount, purchaseCount);
    }

    private void create(JsonBusiness jb, User user, Lookup lookup, Instant now) {
        Business b = new Business();
        b.setName(ImportParser.truncate(jb.name().trim(), 160));
        b.setLegalName(ImportParser.truncate(jb.legalName(), 200));
        b.setType(jb.type() == null ? null : types.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(jb.type(), jb.type()).orElse(null));
        b.setStatus(parse(BusinessStatus.class, jb.status(), BusinessStatus.NEW));
        b.setPriority(parse(Priority.class, jb.priority(), Priority.NORMAL));
        b.setAddress(ImportParser.truncate(jb.address(), 255));
        b.setCity(ImportParser.truncate(jb.city(), 80));
        b.setDistrict(ImportParser.truncate(jb.district(), 80));
        b.setPhone(ImportParser.truncate(jb.phone(), 60));
        b.setEmail(ImportParser.truncate(jb.email(), 120));
        b.setWebsite(ImportParser.truncate(jb.website(), 200));
        b.setMapsUrl(ImportParser.truncate(jb.mapsUrl(), 500));
        b.setIdCode(ImportParser.truncate(jb.idCode(), 40));
        b.setBranches(jb.branches());
        b.setVisitHours(ImportParser.truncate(jb.visitHours(), 120));
        b.setNotes(jb.notes());
        b.setMenuChange(ImportParser.truncate(jb.menuChange(), 200));
        b.setSwitchOpenness(parse(Openness.class, jb.switchOpenness(), Openness.UNKNOWN));
        b.setPriceSensitivity(parse(PriceSensitivity.class, jb.priceSensitivity(), PriceSensitivity.UNKNOWN));
        b.setSatisfaction(parse(Satisfaction.class, jb.satisfaction(), Satisfaction.UNKNOWN));
        b.setCompetitorNotes(jb.competitorNotes());
        b.setReorderDays(jb.reorderDays());
        if (jb.drinkTypes() != null) {
            jb.drinkTypes().forEach(d -> {
                DrinkType type = parse(DrinkType.class, d, null);
                if (type != null) {
                    b.getDrinkTypes().add(type);
                }
            });
        }
        User named = lookup.user(jb.assignedTo());
        b.setAssignedTo(user.isSupervisor() ? named : user);
        b.setCreatedBy(user);
        b.setCreatedAt(jb.createdAt() == null ? now : jb.createdAt());
        b.setUpdatedAt(now);
        if (jb.sheet() != null && !jb.sheet().isBlank()) {
            var project = jb.project() == null || jb.project().isBlank() ? null : workspace.workbook(jb.project(), null, user);
            b.setSheet(workspace.sheet(jb.sheet(), project, user));
        }
        Business saved = businesses.save(b);
        statusChanges.save(new StatusChange(saved, null, saved.getStatus(), user, "JSON"));
        if (jb.customFields() != null) {
            jb.customFields().forEach((label, value) -> {
                if (label != null && !label.isBlank() && value != null && !value.isBlank()) {
                    businessService.setCustomValue(saved, workspace.field(label, user).getId(), value);
                }
            });
        }

        Map<String, Contact> contactByName = new HashMap<>();
        for (JsonContact jc : list(jb.contacts())) {
            if (Text.blankToNull(jc.name()) == null) {
                continue;
            }
            Contact contact = new Contact();
            contact.setBusiness(saved);
            contact.setName(ImportParser.truncate(jc.name().trim(), 120));
            contact.setRoleTitle(ImportParser.truncate(jc.roleTitle(), 80));
            contact.setPhone(ImportParser.truncate(jc.phone(), 60));
            contact.setEmail(ImportParser.truncate(jc.email(), 120));
            contact.setPreferredChannel(parse(ContactChannel.class, jc.preferredChannel(), ContactChannel.ANY));
            contact.setDecisionMaker(Boolean.TRUE.equals(jc.decisionMaker()));
            contact.setNotes(jc.notes());
            contactByName.put(contact.getName(), contacts.save(contact));
        }
        for (JsonAnswer answer : list(jb.categoryAnswers())) {
            ProductCategory category = lookup.category(answer.category());
            if (category != null) {
                businessService.upsertCategoryAnswer(saved, category.getId(), parse(UsageAnswer.class, answer.answer(), UsageAnswer.UNKNOWN), answer.notes());
            }
        }
        for (JsonUsage ju : list(jb.usages())) {
            ProductCategory category = lookup.category(ju.category());
            if (category == null) {
                continue;
            }
            ProductUsage usage = new ProductUsage();
            usage.setBusiness(saved);
            usage.setCategory(category);
            usage.setBrand(lookup.brand(ju.brand()));
            usage.setFlavor(lookup.flavor(ju.flavor()));
            usage.setProductName(ImportParser.truncate(ju.productName(), 160));
            usage.setQuantity(ImportParser.truncate(ju.quantity(), 80));
            usage.setFrequency(ImportParser.truncate(ju.frequency(), 80));
            usage.setNotes(ju.notes());
            usage.setCreatedBy(user);
            usages.save(usage);
        }
        for (JsonInterest ji : list(jb.interests())) {
            Flavor flavor = lookup.flavor(ji.flavor());
            Product product = lookup.product(ji.product());
            if (flavor == null && product == null) {
                continue;
            }
            Interest interest = new Interest();
            interest.setBusiness(saved);
            interest.setFlavor(flavor);
            interest.setProduct(product);
            interest.setStatus(parse(InterestStatus.class, ji.status(), InterestStatus.INTERESTED));
            interest.setReason(parse(InterestReason.class, ji.reason(), InterestReason.GENERAL));
            interest.setFeedback(parse(TastingFeedback.class, ji.feedback(), TastingFeedback.UNKNOWN));
            interest.setNotes(ji.notes());
            interest.setCreatedBy(user);
            interests.save(interest);
        }
        Instant lastContact = null;
        for (JsonActivity ja : list(jb.activities())) {
            Activity activity = new Activity();
            activity.setBusiness(saved);
            activity.setUser(Objects.requireNonNullElse(lookup.user(ja.user()), user));
            activity.setType(parse(ActivityType.class, ja.type(), ActivityType.OTHER));
            activity.setResult(parse(ActivityResult.class, ja.result(), ActivityResult.OTHER));
            activity.setOccurredAt(ja.occurredAt() == null ? now : ja.occurredAt());
            activity.setNotes(ja.notes());
            activity.setContact(ja.contact() == null ? null : contactByName.get(ja.contact()));
            activity.setImported(Boolean.TRUE.equals(ja.imported()));
            activities.save(activity);
            if (lastContact == null || activity.getOccurredAt().isAfter(lastContact)) {
                lastContact = activity.getOccurredAt();
            }
        }
        saved.setLastContactAt(lastContact);
        for (JsonTask jt : list(jb.tasks())) {
            if (jt.dueAt() == null) {
                continue;
            }
            Task task = new Task();
            task.setBusiness(saved);
            task.setType(parse(TaskType.class, jt.type(), TaskType.FOLLOW_UP));
            task.setTitle(ImportParser.truncate(jt.title(), 200));
            task.setDueAt(jt.dueAt());
            task.setEndAt(jt.endAt());
            task.setAllDay(Boolean.TRUE.equals(jt.allDay()));
            task.setLocation(ImportParser.truncate(jt.location(), 200));
            task.setPriority(parse(Priority.class, jt.priority(), Priority.NORMAL));
            task.setStatus(parse(TaskStatus.class, jt.status(), TaskStatus.OPEN));
            task.setNotes(jt.notes());
            User assignee = lookup.user(jt.assignedTo());
            task.setAssignedTo(assignee != null && user.isSupervisor() ? assignee : saved.getAssignedTo() != null ? saved.getAssignedTo() : user);
            task.setCreatedBy(user);
            if (task.getStatus() != TaskStatus.OPEN) {
                task.setCompletedAt(jt.dueAt());
                task.setCompletedBy(task.getAssignedTo());
            }
            tasks.save(task);
        }
        for (JsonComment jc : list(jb.comments())) {
            if (Text.blankToNull(jc.body()) == null) {
                continue;
            }
            Comment comment = new Comment();
            comment.setBusiness(saved);
            comment.setAuthor(Objects.requireNonNullElse(lookup.user(jc.author()), user));
            comment.setBody(jc.body());
            comment.setCreatedAt(jc.createdAt() == null ? now : jc.createdAt());
            comments.save(comment);
        }
        LocalDate lastPurchase = null;
        int orders = 0;
        for (JsonPurchase jp : list(jb.purchases())) {
            if (jp.purchaseDate() == null || jp.items() == null || jp.items().isEmpty()) {
                continue;
            }
            Purchase purchase = new Purchase();
            purchase.setBusiness(saved);
            purchase.setUser(Objects.requireNonNullElse(lookup.user(jp.user()), user));
            purchase.setPurchaseDate(jp.purchaseDate());
            purchase.setNotes(jp.notes());
            BigDecimal total = BigDecimal.ZERO;
            for (JsonItem ji : jp.items()) {
                Product product = lookup.product(ji.product());
                BigDecimal quantity = ji.quantity() == null ? BigDecimal.ONE : ji.quantity();
                BigDecimal price = ji.unitPrice() == null ? (product != null && product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO) : ji.unitPrice();
                PurchaseItem item = new PurchaseItem();
                item.setPurchase(purchase);
                item.setProduct(product);
                item.setDescription(ImportParser.truncate(Text.blankToNull(ji.description()) != null ? ji.description()
                        : product != null ? product.getNameKa() : "-", 200));
                item.setQuantity(quantity);
                item.setUnitPrice(price);
                item.setLineTotal(quantity.multiply(price).setScale(2, RoundingMode.HALF_UP));
                total = total.add(item.getLineTotal());
                purchase.getItems().add(item);
            }
            purchase.setTotal(total);
            purchases.save(purchase);
            orders++;
            if (lastPurchase == null || jp.purchaseDate().isAfter(lastPurchase)) {
                lastPurchase = jp.purchaseDate();
            }
        }
        saved.setPurchaseCount(orders);
        saved.setLastPurchaseDate(lastPurchase);
        audit.record(saved.getId(), "Business", saved.getId(), "IMPORTED", "JSON", user);
    }

    /** Name lookups, remembered for the length of one import. Unknown brands and flavors are added. */
    private final class Lookup {

        private final Map<String, Optional<ProductCategory>> categoriesByName = new HashMap<>();
        private final Map<String, Brand> brandsByName = new HashMap<>();
        private final Map<String, Flavor> flavorsByName = new HashMap<>();
        private final Map<String, Optional<Product>> productsByName = new HashMap<>();
        private final Map<String, Optional<User>> usersByName = new HashMap<>();

        ProductCategory category(String name) {
            if (Text.blankToNull(name) == null) {
                return null;
            }
            return categoriesByName.computeIfAbsent(name.toLowerCase(Locale.ROOT),
                    key -> categories.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(name, name)).orElse(null);
        }

        Brand brand(String name) {
            if (Text.blankToNull(name) == null) {
                return null;
            }
            return brandsByName.computeIfAbsent(name.toLowerCase(Locale.ROOT),
                    key -> brands.findByNameIgnoreCase(name.trim()).orElseGet(() -> brands.save(new Brand(name.trim(), false))));
        }

        Flavor flavor(String name) {
            if (Text.blankToNull(name) == null) {
                return null;
            }
            return flavorsByName.computeIfAbsent(name.toLowerCase(Locale.ROOT),
                    key -> flavors.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(name.trim(), name.trim())
                            .orElseGet(() -> flavors.save(new Flavor(name.trim(), name.trim()))));
        }

        /** Our product of that name first, then anyone's. Never created: products are managed on the price list. */
        Product product(String name) {
            if (Text.blankToNull(name) == null) {
                return null;
            }
            return productsByName.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> products.findAllForCatalog().stream()
                    .filter(p -> p.getNameEn().equalsIgnoreCase(name.trim()) || p.getNameKa().equalsIgnoreCase(name.trim()))
                    .min(Comparator.comparing((Product p) -> p.getBrand().isOwn() ? 0 : 1))).orElse(null);
        }

        User user(String username) {
            if (Text.blankToNull(username) == null) {
                return null;
            }
            return usersByName.computeIfAbsent(username.toLowerCase(Locale.ROOT),
                    key -> users.findByUsernameIgnoreCase(username.trim()).filter(User::isActive)).orElse(null);
        }
    }

    static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
