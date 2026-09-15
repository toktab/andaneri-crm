package ge.andaneri.crm.service;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.common.Text;
import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.Activity;
import ge.andaneri.crm.domain.ActivityRepository;
import ge.andaneri.crm.domain.Brand;
import ge.andaneri.crm.domain.BrandRepository;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessFieldValue;
import ge.andaneri.crm.domain.BusinessFieldValueRepository;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.BusinessSheetRepository;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.CustomField;
import ge.andaneri.crm.domain.CustomFieldRepository;
import ge.andaneri.crm.web.BusinessDtos.BulkRequest;
import ge.andaneri.crm.web.BusinessDtos.BulkResult;
import ge.andaneri.crm.web.BusinessDtos.GridRow;
import ge.andaneri.crm.domain.BusinessTypeRepository;
import ge.andaneri.crm.domain.CategoryUsage;
import ge.andaneri.crm.domain.CategoryUsageRepository;
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
import ge.andaneri.crm.domain.ProductStatus;
import ge.andaneri.crm.domain.ProductUsage;
import ge.andaneri.crm.domain.ProductUsageRepository;
import ge.andaneri.crm.domain.Purchase;
import ge.andaneri.crm.domain.PurchaseRepository;
import ge.andaneri.crm.domain.Satisfaction;
import ge.andaneri.crm.domain.StatusChange;
import ge.andaneri.crm.domain.StatusChangeRepository;
import ge.andaneri.crm.domain.Task;
import ge.andaneri.crm.domain.TaskRepository;
import ge.andaneri.crm.domain.TaskStatus;
import ge.andaneri.crm.domain.UsageAnswer;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.web.BusinessDtos.BusinessDetail;
import ge.andaneri.crm.web.BusinessDtos.BusinessRequest;
import ge.andaneri.crm.web.BusinessDtos.BusinessSummary;
import ge.andaneri.crm.web.BusinessDtos.CategoryUsageDto;
import ge.andaneri.crm.web.BusinessDtos.CategoryUsageRequest;
import ge.andaneri.crm.web.BusinessDtos.ContactDto;
import ge.andaneri.crm.web.BusinessDtos.ContactRequest;
import ge.andaneri.crm.web.BusinessDtos.DuplicateDto;
import ge.andaneri.crm.web.BusinessDtos.InterestDto;
import ge.andaneri.crm.web.BusinessDtos.InterestRequest;
import ge.andaneri.crm.web.BusinessDtos.InterestUpdate;
import ge.andaneri.crm.web.BusinessDtos.NextTaskRef;
import ge.andaneri.crm.web.BusinessDtos.PageDto;
import ge.andaneri.crm.web.BusinessDtos.PurchaseSummary;
import ge.andaneri.crm.web.BusinessDtos.Suggestion;
import ge.andaneri.crm.web.BusinessDtos.UsageDto;
import ge.andaneri.crm.web.BusinessDtos.UsageRequest;
import ge.andaneri.crm.web.BusinessDtos.UsageUpdate;
import ge.andaneri.crm.web.CatalogDtos.ProductRef;
import ge.andaneri.crm.web.UserDtos.UserRef;
import ge.andaneri.crm.domain.TastingFeedback;
import ge.andaneri.crm.web.WorkDtos.ActivityDto;
import ge.andaneri.crm.web.WorkDtos.TaskDto;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Businesses and what hangs directly off them: contacts, what they use, what they want. */
@Service
public class BusinessService {

    private final BusinessRepository businesses;
    private final BusinessTypeRepository types;
    private final UserRepository users;
    private final ContactRepository contacts;
    private final CategoryUsageRepository categoryUsages;
    private final ProductCategoryRepository categories;
    private final ProductUsageRepository usages;
    private final InterestRepository interests;
    private final BrandRepository brands;
    private final FlavorRepository flavors;
    private final ProductRepository products;
    private final TaskRepository tasks;
    private final PurchaseRepository purchases;
    private final ActivityRepository activities;
    private final StatusChangeRepository statusChanges;
    private final AuditService audit;
    private final SettingsService settings;
    private final CrmProperties properties;
    private final BusinessSheetRepository sheets;
    private final CustomFieldRepository customFields;
    private final BusinessFieldValueRepository fieldValues;

    public BusinessService(BusinessRepository businesses, BusinessTypeRepository types, UserRepository users,
            ContactRepository contacts, CategoryUsageRepository categoryUsages, ProductCategoryRepository categories,
            ProductUsageRepository usages, InterestRepository interests, BrandRepository brands, FlavorRepository flavors,
            ProductRepository products, TaskRepository tasks, PurchaseRepository purchases, ActivityRepository activities,
            StatusChangeRepository statusChanges, AuditService audit, SettingsService settings, CrmProperties properties,
            BusinessSheetRepository sheets, CustomFieldRepository customFields, BusinessFieldValueRepository fieldValues) {
        this.sheets = sheets;
        this.customFields = customFields;
        this.fieldValues = fieldValues;
        this.businesses = businesses;
        this.types = types;
        this.users = users;
        this.contacts = contacts;
        this.categoryUsages = categoryUsages;
        this.categories = categories;
        this.usages = usages;
        this.interests = interests;
        this.brands = brands;
        this.flavors = flavors;
        this.products = products;
        this.tasks = tasks;
        this.purchases = purchases;
        this.activities = activities;
        this.statusChanges = statusChanges;
        this.audit = audit;
        this.settings = settings;
        this.properties = properties;
    }

    // ================================================================== reading

    @Transactional(readOnly = true)
    public PageDto<BusinessSummary> search(BusinessFilter filter, int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Page<Business> result = businesses.findAll(spec(filter), PageRequest.of(safePage, safeSize, sortOf(sort)));
        List<Long> ids = result.getContent().stream().map(Business::getId).toList();

        Map<Long, NextTaskRef> nextTasks = new HashMap<>();
        Map<Long, List<String>> brandNames = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Task task : tasks.findForBusinesses(ids, TaskStatus.OPEN)) {
                nextTasks.putIfAbsent(task.getBusiness().getId(),
                        new NextTaskRef(task.getId(), task.getType().name(), task.getDueAt()));
            }
            for (Object[] row : usages.findBrandNames(ids)) {
                brandNames.computeIfAbsent((Long) row[0], key -> new ArrayList<>()).add((String) row[1]);
            }
        }
        List<BusinessSummary> items = result.getContent().stream()
                .map(b -> new BusinessSummary(b.getId(), b.getName(), b.getType() == null ? null : b.getType().getId(),
                        b.getStatus(), b.getPriority(), b.getAddress(), b.getDistrict(), b.getCity(), b.getPhone(),
                        b.getMapsUrl(), UserRef.of(b.getAssignedTo()), b.getLastContactAt(), b.getLastPurchaseDate(),
                        b.getPurchaseCount(), nextTasks.get(b.getId()),
                        brandNames.getOrDefault(b.getId(), List.of()).stream().sorted().toList(), b.isArchived(),
                        b.getSheet() == null ? null : b.getSheet().getId()))
                .toList();
        return new PageDto<>(items, result.getTotalElements(), safePage, safeSize);
    }

    /** The whole filtered list, for the Excel export. */
    @Transactional(readOnly = true)
    public List<Business> findAll(BusinessFilter filter, String sort) {
        List<Business> all = businesses.findAll(spec(filter), sortOf(sort));
        // Touch what the export reads while the session is open.
        all.forEach(b -> {
            if (b.getAssignedTo() != null) {
                b.getAssignedTo().getFullName();
            }
            if (b.getType() != null) {
                b.getType().getNameKa();
            }
            if (b.getSheet() != null && b.getSheet().getWorkbook() != null) {
                b.getSheet().getWorkbook().getName();
            }
        });
        return all;
    }

    @Transactional(readOnly = true)
    public BusinessDetail detail(Long id, User user) {
        Business b = load(id);
        List<ProductUsage> usageRows = usages.findForBusiness(id);
        List<Interest> interestRows = interests.findForBusiness(id);
        List<TaskDto> openTasks = tasks.findForBusiness(id).stream()
                .filter(t -> t.getStatus() == TaskStatus.OPEN)
                .map(TaskDto::of)
                .toList();
        List<Contact> contactRows = contacts.findByBusinessIdOrderByDecisionMakerDescNameAsc(id);
        List<CategoryUsage> answers = categoryUsages.findByBusinessId(id);
        List<Activity> history = activities.findForBusiness(id);
        return BusinessDetail.of(b,
                contactRows.stream().map(ContactDto::of).toList(),
                answers.stream().map(CategoryUsageDto::of).toList(),
                usageRows.stream().map(UsageDto::of).toList(),
                interestRows.stream().map(InterestDto::of).toList(),
                openTasks,
                purchaseSummary(b),
                suggestions(b, usageRows, interestRows),
                history.isEmpty() ? null : ActivityDto.of(history.get(0)),
                missingInfo(b, contactRows, answers, usageRows, !openTasks.isEmpty()),
                CurrentUser.canEdit(user, b),
                customValues(id));
    }

    /**
     * The questions still open about a business, as codes the frontend turns into red "ask them"
     * chips during a call or visit. Only what a salesperson can actually find out by asking.
     */
    List<String> missingInfo(Business b, List<Contact> contactRows, List<CategoryUsage> answers,
            List<ProductUsage> usageRows, boolean hasOpenTask) {
        List<String> missing = new ArrayList<>();
        boolean gone = b.getStatus() == BusinessStatus.LOST || b.getStatus() == BusinessStatus.NOT_INTERESTED;
        boolean anyPhone = b.getPhone() != null || contactRows.stream().anyMatch(c -> c.getPhone() != null);
        if (!anyPhone) {
            missing.add("PHONE");
        }
        if (contactRows.isEmpty()) {
            missing.add("CONTACT_PERSON");
        } else if (contactRows.stream().noneMatch(Contact::isDecisionMaker)) {
            missing.add("DECISION_MAKER");
        }

        Long syrupId = categoryId("სიროფი", "Syrup");
        Long pureeId = categoryId("ფრუტ-პიურე", "Fruit puree");
        UsageAnswer syrupAnswer = answerFor(answers, syrupId);
        List<ProductUsage> syrupRows = usageRows.stream()
                .filter(u -> syrupId != null && u.getCategory().getId().equals(syrupId)).toList();
        boolean usesSyrup = syrupAnswer == UsageAnswer.YES || syrupAnswer == UsageAnswer.SOMETIMES || !syrupRows.isEmpty();
        if (syrupId != null && syrupAnswer == UsageAnswer.UNKNOWN && syrupRows.isEmpty()) {
            missing.add("USES_SYRUP");
        } else if (usesSyrup) {
            if (syrupRows.stream().noneMatch(u -> u.getBrand() != null)) {
                missing.add("SYRUP_BRAND");
            }
            if (syrupRows.stream().noneMatch(u -> u.getFlavor() != null)) {
                missing.add("SYRUP_FLAVORS");
            }
            boolean competitor = syrupRows.stream().anyMatch(u -> u.getBrand() != null && !u.getBrand().isOwn());
            if (competitor && b.getSwitchOpenness() == ge.andaneri.crm.domain.Openness.UNKNOWN) {
                missing.add("SWITCH_OPENNESS");
            }
        }
        if (pureeId != null && answerFor(answers, pureeId) == UsageAnswer.UNKNOWN) {
            missing.add("USES_PUREE");
        }
        if (b.getDrinkTypes().isEmpty()) {
            missing.add("DRINK_TYPES");
        }
        if (b.getVisitHours() == null) {
            missing.add("VISIT_HOURS");
        }
        if (b.getType() == null) {
            missing.add("TYPE");
        }
        if (b.getAddress() == null) {
            missing.add("ADDRESS");
        }
        boolean customer = b.getStatus() == BusinessStatus.CUSTOMER || b.getStatus() == BusinessStatus.REPEAT_CUSTOMER;
        if (customer && b.getIdCode() == null) {
            missing.add("ID_CODE");
        }
        if (!hasOpenTask && !gone) {
            missing.add("NEXT_STEP");
        }
        return missing;
    }

    private Long categoryId(String nameKa, String nameEn) {
        return categories.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(nameKa, nameEn).map(ProductCategory::getId).orElse(null);
    }

    private static UsageAnswer answerFor(List<CategoryUsage> answers, Long categoryId) {
        return answers.stream()
                .filter(a -> categoryId != null && a.getCategory().getId().equals(categoryId))
                .map(CategoryUsage::getAnswer)
                .findFirst().orElse(UsageAnswer.UNKNOWN);
    }

    @Transactional(readOnly = true)
    public List<DuplicateDto> findDuplicates(String name, String address, String phone, String idCode, Long excludeId) {
        return DuplicateIndex.of(businesses.findByArchivedFalse()).matches(name, address, phone, idCode, excludeId);
    }

    private PurchaseSummary purchaseSummary(Business b) {
        List<Purchase> list = purchases.findForBusiness(b.getId());
        List<LocalDate> dates = list.stream().map(Purchase::getPurchaseDate).sorted().toList();
        Integer average = Reorder.averageGap(dates);
        int expected = Reorder.expectedDays(b.getReorderDays(), average, settings.getInt(SettingsService.REORDER_DAYS));
        LocalDate last = dates.isEmpty() ? null : dates.get(dates.size() - 1);
        BigDecimal total = list.stream().map(Purchase::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseSummary(list.size(), last, total, average, expected,
                last == null ? null : last.plusDays(expected),
                last == null ? null : ChronoUnit.DAYS.between(last, today()));
    }

    /**
     * What to offer this business, best reasons first: our product for each competitor flavor
     * they use, then for each flavor or product they asked about, then signature syrups that fit
     * what they make. TREND suggestions carry the matching drink types as comma-separated enum
     * names, for the frontend to translate.
     */
    private List<Suggestion> suggestions(Business b, List<ProductUsage> usageRows, List<Interest> interestRows) {
        List<Product> ours = products.findAllForCatalog().stream()
                .filter(p -> p.getBrand().isOwn() && p.getStatus() != ProductStatus.INACTIVE)
                .toList();
        Set<Long> flavorsAlreadyOurs = usageRows.stream()
                .filter(u -> u.getBrand() != null && u.getBrand().isOwn() && u.getFlavor() != null)
                .map(u -> u.getFlavor().getId())
                .collect(Collectors.toSet());
        Map<Long, Suggestion> out = new LinkedHashMap<>();

        for (ProductUsage usage : usageRows) {
            if (usage.getFlavor() == null || usage.getBrand() == null || usage.getBrand().isOwn()
                    || flavorsAlreadyOurs.contains(usage.getFlavor().getId())) {
                continue;
            }
            String brand = usage.getBrand().getName() + " ";
            for (Product product : withFlavor(ours, usage.getFlavor().getId())) {
                out.putIfAbsent(product.getId(), suggestion("REPLACE", product,
                        brand + usage.getFlavor().getNameKa(), brand + usage.getFlavor().getNameEn()));
            }
        }
        for (Interest interest : interestRows) {
            if (interest.getStatus() == InterestStatus.NOT_INTERESTED || interest.getStatus() == InterestStatus.PURCHASED) {
                continue;
            }
            if (interest.getProduct() != null && interest.getProduct().getBrand().isOwn()) {
                Product product = interest.getProduct();
                out.putIfAbsent(product.getId(), suggestion("INTEREST", product, product.getNameKa(), product.getNameEn()));
            } else if (interest.getFlavor() != null) {
                for (Product product : withFlavor(ours, interest.getFlavor().getId())) {
                    out.putIfAbsent(product.getId(), suggestion("INTEREST", product,
                            interest.getFlavor().getNameKa(), interest.getFlavor().getNameEn()));
                }
            }
        }
        Set<DrinkType> makes = b.getDrinkTypes();
        if (!makes.isEmpty()) {
            ours.stream()
                    .filter(p -> "Signature Syrups".equals(p.getSectionEn()) && p.getStatus() == ProductStatus.ACTIVE)
                    .map(p -> Map.entry(p, p.getApplications().stream().filter(makes::contains).map(Enum::name).toList()))
                    .filter(entry -> !entry.getValue().isEmpty())
                    .sorted(Comparator.comparing(entry -> -entry.getValue().size()))
                    .limit(4)
                    .forEach(entry -> {
                        String match = String.join(",", entry.getValue());
                        out.putIfAbsent(entry.getKey().getId(), suggestion("TREND", entry.getKey(), match, match));
                    });
        }
        return out.values().stream().limit(12).toList();
    }

    /** Our products with this flavor, single-flavor ones first ("Mango" before "Dragon Fruit & Mango"), at most two. */
    private static List<Product> withFlavor(List<Product> ours, Long flavorId) {
        return ours.stream()
                .filter(p -> p.getFlavors().stream().anyMatch(f -> f.getId().equals(flavorId)))
                .sorted(Comparator.comparing((Product p) -> p.getFlavors().size())
                        .thenComparing(p -> p.getStatus() == ProductStatus.ACTIVE ? 0 : 1))
                .limit(2)
                .toList();
    }

    private static Suggestion suggestion(String kind, Product product, String matchKa, String matchEn) {
        return new Suggestion(kind, ProductRef.of(product), product.getSectionKa(), product.getSectionEn(), matchKa, matchEn);
    }

    // ================================================================== writing

    @Transactional
    public BusinessDetail create(BusinessRequest request, boolean force, User user) {
        if (!force) {
            List<DuplicateDto> duplicates = findDuplicates(request.name(), request.address(), request.phone(), request.idCode(), null);
            if (!duplicates.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE", Map.of("duplicates", duplicates));
            }
        }
        Business b = new Business();
        apply(b, request);
        Instant now = Instant.now();
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        b.setCreatedBy(user);
        // A salesperson's new lead is theirs; a supervisor may add it for someone else or leave it open.
        if (user.isSupervisor()) {
            b.setAssignedTo(request.assignedToId() == null ? null : activeUser(request.assignedToId()));
        } else {
            b.setAssignedTo(user);
        }
        b.setStatus(request.status() == null ? BusinessStatus.NEW : request.status());
        b = businesses.save(b);
        statusChanges.save(new StatusChange(b, null, b.getStatus(), user, null));
        saveCustomValues(b, request.customValues());
        if (request.firstContact() != null) {
            Contact contact = new Contact();
            contact.setBusiness(b);
            applyContact(contact, request.firstContact());
            contacts.save(contact);
        }
        audit.record(b.getId(), "Business", b.getId(), "CREATED", b.getName(), user);
        return detail(b.getId(), user);
    }

    @Transactional
    public BusinessDetail update(Long id, BusinessRequest request, User user) {
        Business b = load(id);
        CurrentUser.requireEdit(user, b);
        if (request.version() != null && request.version() != b.getVersion()) {
            throw ApiException.conflict("STALE");
        }
        List<String> changed = changedFields(b, request);
        apply(b, request);
        if (request.status() != null && request.status() != b.getStatus()) {
            applyStatus(b, request.status(), user, null);
        }
        Long currentAssignee = b.getAssignedTo() == null ? null : b.getAssignedTo().getId();
        if (user.isSupervisor() && !Objects.equals(currentAssignee, request.assignedToId())) {
            b.setAssignedTo(request.assignedToId() == null ? null : activeUser(request.assignedToId()));
            changed.add("assignedTo");
        }
        if (request.customValues() != null && !request.customValues().isEmpty()) {
            saveCustomValues(b, request.customValues());
            changed.add("customFields");
        }
        b.setUpdatedAt(Instant.now());
        if (!changed.isEmpty()) {
            audit.record(id, "Business", id, "UPDATED", String.join(", ", changed), user);
        }
        businesses.saveAndFlush(b);
        return detail(id, user);
    }

    @Transactional
    public BusinessDetail changeStatus(Long id, BusinessStatus status, String note, User user) {
        Business b = load(id);
        CurrentUser.requireEdit(user, b);
        applyStatus(b, status, user, Text.blankToNull(note));
        return detail(id, user);
    }

    /** Moves a business to another pipeline stage and writes the history row. No-op when it is already there. */
    public void applyStatus(Business b, BusinessStatus to, User user, String note) {
        if (to == null || to == b.getStatus()) {
            return;
        }
        BusinessStatus from = b.getStatus();
        statusChanges.save(new StatusChange(b, from, to, user, note));
        b.setStatus(to);
        b.setUpdatedAt(Instant.now());
        audit.record(b.getId(), "Business", b.getId(), "STATUS", from + " -> " + to, user);
    }

    @Transactional
    public BusinessDetail assign(Long id, Long userId, User user) {
        Business b = load(id);
        // A salesperson may only claim an unassigned business for themselves.
        boolean claiming = b.getAssignedTo() == null && user.getId().equals(userId);
        if (!user.isSupervisor() && !claiming) {
            throw ApiException.forbidden();
        }
        User target = userId == null ? null : activeUser(userId);
        b.setAssignedTo(target);
        b.setUpdatedAt(Instant.now());
        audit.record(id, "Business", id, "ASSIGNED", target == null ? "-" : target.getFullName(), user);
        return detail(id, user);
    }

    @Transactional
    public BusinessDetail setArchived(Long id, boolean archived, User user) {
        if (!user.isSupervisor()) {
            throw ApiException.forbidden();
        }
        Business b = load(id);
        b.setArchived(archived);
        b.setUpdatedAt(Instant.now());
        audit.record(id, "Business", id, archived ? "ARCHIVED" : "RESTORED", b.getName(), user);
        return detail(id, user);
    }

    // ------------------------------------------------------------ contacts

    @Transactional
    public ContactDto addContact(Long businessId, ContactRequest request, User user) {
        Business b = load(businessId);
        CurrentUser.requireEdit(user, b);
        Contact contact = new Contact();
        contact.setBusiness(b);
        applyContact(contact, request);
        contact = contacts.save(contact);
        audit.record(businessId, "Contact", contact.getId(), "CREATED", contact.getName(), user);
        return ContactDto.of(contact);
    }

    @Transactional
    public ContactDto updateContact(Long businessId, Long contactId, ContactRequest request, User user) {
        Contact contact = contacts.findById(contactId)
                .filter(c -> c.getBusiness().getId().equals(businessId))
                .orElseThrow(ApiException::notFound);
        CurrentUser.requireEdit(user, contact.getBusiness());
        applyContact(contact, request);
        audit.record(businessId, "Contact", contactId, "UPDATED", contact.getName(), user);
        return ContactDto.of(contact);
    }

    @Transactional
    public void deleteContact(Long businessId, Long contactId, User user) {
        Contact contact = contacts.findById(contactId)
                .filter(c -> c.getBusiness().getId().equals(businessId))
                .orElseThrow(ApiException::notFound);
        CurrentUser.requireEdit(user, contact.getBusiness());
        audit.record(businessId, "Contact", contactId, "DELETED", contact.getName(), user);
        contacts.delete(contact);
    }

    // ------------------------------------------------------------ what they use

    @Transactional
    public List<CategoryUsageDto> setCategoryAnswers(Long businessId, List<CategoryUsageRequest> answers, User user) {
        Business b = load(businessId);
        CurrentUser.requireEdit(user, b);
        for (CategoryUsageRequest answer : answers) {
            upsertCategoryAnswer(b, answer.categoryId(), answer.answer(), answer.notes());
        }
        audit.record(businessId, "CategoryUsage", null, "UPDATED", answers.size() + " answers", user);
        return categoryUsages.findByBusinessId(businessId).stream().map(CategoryUsageDto::of).toList();
    }

    /** Sets "do they use X?" for one category. {@code notes} null keeps the existing notes. */
    public void upsertCategoryAnswer(Business b, Long categoryId, UsageAnswer answer, String notes) {
        CategoryUsage usage = categoryUsages.findByBusinessIdAndCategoryId(b.getId(), categoryId).orElseGet(() -> {
            CategoryUsage created = new CategoryUsage();
            created.setBusiness(b);
            created.setCategory(category(categoryId));
            return created;
        });
        usage.setAnswer(answer);
        if (notes != null) {
            usage.setNotes(Text.blankToNull(notes));
        }
        usage.setUpdatedAt(Instant.now());
        categoryUsages.save(usage);
    }

    @Transactional
    public List<UsageDto> addUsages(Long businessId, UsageRequest request, User user) {
        Business b = load(businessId);
        CurrentUser.requireEdit(user, b);
        addUsagesTo(b, request, user);
        return usages.findForBusiness(businessId).stream().map(UsageDto::of).toList();
    }

    /**
     * One row per flavor, skipping rows that already exist, and answers "do they use this
     * category?" with yes if it was not already yes or sometimes: they plainly do.
     */
    public void addUsagesTo(Business b, UsageRequest request, User user) {
        ProductCategory category = category(request.categoryId());
        Brand brand = request.brandId() == null ? null
                : brands.findById(request.brandId()).orElseThrow(() -> ApiException.field("brandId", "invalid"));
        List<Long> flavorIds = request.flavorIds() == null || request.flavorIds().isEmpty()
                ? java.util.Collections.singletonList(null) : request.flavorIds();
        List<ProductUsage> existing = usages.findForBusiness(b.getId());
        List<String> added = new ArrayList<>();
        for (Long flavorId : flavorIds) {
            boolean duplicate = existing.stream().anyMatch(u -> u.getCategory().getId().equals(category.getId())
                    && Objects.equals(u.getBrand() == null ? null : u.getBrand().getId(), brand == null ? null : brand.getId())
                    && Objects.equals(u.getFlavor() == null ? null : u.getFlavor().getId(), flavorId));
            if (duplicate) {
                continue;
            }
            Flavor flavor = flavorId == null ? null : flavor(flavorId);
            ProductUsage usage = new ProductUsage();
            usage.setBusiness(b);
            usage.setCategory(category);
            usage.setBrand(brand);
            usage.setFlavor(flavor);
            usage.setProductName(Text.blankToNull(request.productName()));
            usage.setQuantity(Text.blankToNull(request.quantity()));
            usage.setFrequency(Text.blankToNull(request.frequency()));
            usage.setNotes(Text.blankToNull(request.notes()));
            usage.setCreatedBy(user);
            usages.save(usage);
            added.add(flavor == null ? "-" : flavor.getNameKa());
        }
        if (added.isEmpty()) {
            return;
        }
        UsageAnswer current = categoryUsages.findByBusinessIdAndCategoryId(b.getId(), category.getId())
                .map(CategoryUsage::getAnswer).orElse(UsageAnswer.UNKNOWN);
        if (current != UsageAnswer.YES && current != UsageAnswer.SOMETIMES) {
            upsertCategoryAnswer(b, category.getId(), UsageAnswer.YES, null);
        }
        audit.record(b.getId(), "Usage", null, "CREATED",
                (brand == null ? "" : brand.getName() + ": ") + String.join(", ", added), user);
    }

    @Transactional
    public List<UsageDto> updateUsage(Long businessId, Long usageId, UsageUpdate request, User user) {
        ProductUsage usage = usages.findById(usageId)
                .filter(u -> u.getBusiness().getId().equals(businessId))
                .orElseThrow(ApiException::notFound);
        CurrentUser.requireEdit(user, usage.getBusiness());
        usage.setCategory(category(request.categoryId()));
        usage.setBrand(request.brandId() == null ? null
                : brands.findById(request.brandId()).orElseThrow(() -> ApiException.field("brandId", "invalid")));
        usage.setFlavor(request.flavorId() == null ? null : flavor(request.flavorId()));
        usage.setProductName(Text.blankToNull(request.productName()));
        usage.setQuantity(Text.blankToNull(request.quantity()));
        usage.setFrequency(Text.blankToNull(request.frequency()));
        usage.setNotes(Text.blankToNull(request.notes()));
        audit.record(businessId, "Usage", usageId, "UPDATED", null, user);
        return usages.findForBusiness(businessId).stream().map(UsageDto::of).toList();
    }

    @Transactional
    public List<UsageDto> deleteUsage(Long businessId, Long usageId, User user) {
        ProductUsage usage = usages.findById(usageId)
                .filter(u -> u.getBusiness().getId().equals(businessId))
                .orElseThrow(ApiException::notFound);
        CurrentUser.requireEdit(user, usage.getBusiness());
        audit.record(businessId, "Usage", usageId, "DELETED",
                (usage.getBrand() == null ? "" : usage.getBrand().getName() + ": ")
                        + (usage.getFlavor() == null ? "-" : usage.getFlavor().getNameKa()), user);
        usages.delete(usage);
        usages.flush();
        return usages.findForBusiness(businessId).stream().map(UsageDto::of).toList();
    }

    // ------------------------------------------------------------ what they want

    @Transactional
    public List<InterestDto> addInterests(Long businessId, InterestRequest request, User user) {
        Business b = load(businessId);
        CurrentUser.requireEdit(user, b);
        addInterestsTo(b, request, user);
        return interests.findForBusiness(businessId).stream().map(InterestDto::of).toList();
    }

    /** One row per flavor and per product. An existing row for the same flavor or product is updated instead. */
    public void addInterestsTo(Business b, InterestRequest request, User user) {
        InterestStatus status = request.status() == null ? InterestStatus.INTERESTED : request.status();
        InterestReason reason = request.reason() == null ? InterestReason.GENERAL : request.reason();
        List<Interest> existing = interests.findForBusiness(b.getId());
        List<String> names = new ArrayList<>();
        for (Long flavorId : request.flavorIds() == null ? List.<Long>of() : request.flavorIds()) {
            Flavor flavor = flavor(flavorId);
            Interest interest = existing.stream()
                    .filter(i -> i.getProduct() == null && i.getFlavor() != null && i.getFlavor().getId().equals(flavorId))
                    .findFirst().orElseGet(Interest::new);
            interest.setFlavor(flavor);
            saveInterest(interest, b, status, reason, request.feedback(), request.notes(), user);
            names.add(flavor.getNameKa());
        }
        for (Long productId : request.productIds() == null ? List.<Long>of() : request.productIds()) {
            Product product = products.findById(productId).orElseThrow(() -> ApiException.field("productIds", "invalid"));
            Interest interest = existing.stream()
                    .filter(i -> i.getProduct() != null && i.getProduct().getId().equals(productId))
                    .findFirst().orElseGet(Interest::new);
            interest.setProduct(product);
            saveInterest(interest, b, status, reason, request.feedback(), request.notes(), user);
            names.add(product.getNameKa());
        }
        if (!names.isEmpty()) {
            audit.record(b.getId(), "Interest", null, "CREATED", status + ": " + String.join(", ", names), user);
        }
    }

    private void saveInterest(Interest interest, Business b, InterestStatus status, InterestReason reason,
            TastingFeedback feedback, String notes, User user) {
        if (interest.getId() == null) {
            interest.setBusiness(b);
            interest.setCreatedBy(user);
        }
        interest.setStatus(status);
        interest.setReason(reason);
        if (feedback != null) {
            interest.setFeedback(feedback);
        }
        if (Text.blankToNull(notes) != null) {
            interest.setNotes(notes.trim());
        }
        interest.setUpdatedAt(Instant.now());
        interests.save(interest);
    }

    @Transactional
    public List<InterestDto> updateInterest(Long businessId, Long interestId, InterestUpdate request, User user) {
        Interest interest = interests.findById(interestId)
                .filter(i -> i.getBusiness().getId().equals(businessId))
                .orElseThrow(ApiException::notFound);
        CurrentUser.requireEdit(user, interest.getBusiness());
        String before = interest.getStatus().name();
        interest.setStatus(request.status());
        if (request.reason() != null) {
            interest.setReason(request.reason());
        }
        if (request.feedback() != null) {
            interest.setFeedback(request.feedback());
        }
        interest.setNotes(Text.blankToNull(request.notes()));
        interest.setUpdatedAt(Instant.now());
        audit.record(businessId, "Interest", interestId, "UPDATED", before + " -> " + request.status(), user);
        return interests.findForBusiness(businessId).stream().map(InterestDto::of).toList();
    }

    @Transactional
    public List<InterestDto> deleteInterest(Long businessId, Long interestId, User user) {
        Interest interest = interests.findById(interestId)
                .filter(i -> i.getBusiness().getId().equals(businessId))
                .orElseThrow(ApiException::notFound);
        CurrentUser.requireEdit(user, interest.getBusiness());
        audit.record(businessId, "Interest", interestId, "DELETED", null, user);
        interests.delete(interest);
        interests.flush();
        return interests.findForBusiness(businessId).stream().map(InterestDto::of).toList();
    }

    // ================================================================== spreadsheet view

    @Transactional(readOnly = true)
    public PageDto<GridRow> grid(BusinessFilter filter, int page, int size, String sort, User user) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 500);
        Page<Business> result = businesses.findAll(spec(filter), PageRequest.of(safePage, safeSize, sortOf(sort)));
        return new PageDto<>(gridRows(result.getContent(), user), result.getTotalElements(), safePage, safeSize);
    }

    /** Builds rows for a whole page with five queries in total, whatever the page size. */
    private List<GridRow> gridRows(List<Business> rows, User user) {
        List<Long> ids = rows.stream().map(Business::getId).toList();
        Map<Long, NextTaskRef> next = new HashMap<>();
        Map<Long, List<String>> brandNames = new HashMap<>();
        Map<Long, List<Long>> flavorIds = new HashMap<>();
        Map<Long, List<String>> people = new HashMap<>();
        Map<Long, Map<Long, String>> custom = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Task task : tasks.findForBusinesses(ids, TaskStatus.OPEN)) {
                next.putIfAbsent(task.getBusiness().getId(), new NextTaskRef(task.getId(), task.getType().name(), task.getDueAt()));
            }
            for (Object[] row : usages.findBrandNames(ids)) {
                brandNames.computeIfAbsent((Long) row[0], key -> new ArrayList<>()).add((String) row[1]);
            }
            for (Object[] row : usages.findFlavorIds(ids)) {
                flavorIds.computeIfAbsent((Long) row[0], key -> new ArrayList<>()).add((Long) row[1]);
            }
            for (Contact c : contacts.findByBusinessIdIn(ids)) {
                String person = java.util.stream.Stream.of(c.getName(), c.getRoleTitle() == null ? null : "(" + c.getRoleTitle() + ")", c.getPhone())
                        .filter(Objects::nonNull).collect(Collectors.joining(" "));
                people.computeIfAbsent(c.getBusiness().getId(), key -> new ArrayList<>()).add(person);
            }
            for (BusinessFieldValue value : fieldValues.findForBusinesses(ids)) {
                custom.computeIfAbsent(value.getBusiness().getId(), key -> new HashMap<>()).put(value.getField().getId(), value.getValue());
            }
        }
        return rows.stream().map(b -> new GridRow(b.getId(), b.getName(), b.getLegalName(),
                b.getType() == null ? null : b.getType().getId(), b.getStatus(), b.getPriority(), b.getPhone(), b.getEmail(),
                b.getWebsite(), b.getMapsUrl(), b.getAddress(), b.getCity(), b.getDistrict(), b.getIdCode(), b.getBranches(),
                b.getVisitHours(), b.getNotes(), b.getMenuChange(), b.getCompetitorNotes(), b.getSwitchOpenness(),
                b.getPriceSensitivity(), b.getSatisfaction(), b.getReorderDays(), UserRef.of(b.getAssignedTo()),
                b.getSheet() == null ? null : b.getSheet().getId(), b.getLastContactAt(), b.getLastPurchaseDate(),
                b.getPurchaseCount(), next.get(b.getId()), brandNames.getOrDefault(b.getId(), List.of()),
                flavorIds.getOrDefault(b.getId(), List.of()), String.join("; ", people.getOrDefault(b.getId(), List.of())),
                custom.getOrDefault(b.getId(), Map.of()), b.getVersion(), CurrentUser.canEdit(user, b), b.isArchived(),
                b.getCreatedAt())).toList();
    }

    /**
     * Changes one or a few fields, as typed into a spreadsheet cell. Keys are field names, or "custom.&lt;id&gt;"
     * for a custom field; blank text clears a field. Returns the fresh row.
     */
    @Transactional
    public GridRow patch(Long id, Map<String, Object> changes, Long version, User user) {
        Business b = load(id);
        CurrentUser.requireEdit(user, b);
        if (version != null && version != b.getVersion()) {
            throw ApiException.conflict("STALE");
        }
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, Object> entry : (changes == null ? Map.<String, Object>of() : changes).entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case "name" -> {
                    String name = text(value, 160, key);
                    if (name == null) {
                        throw ApiException.field(key, "required");
                    }
                    b.setName(name);
                }
                case "legalName" -> b.setLegalName(text(value, 200, key));
                case "phone" -> b.setPhone(text(value, 60, key));
                case "email" -> {
                    String email = text(value, 120, key);
                    if (email != null && !email.contains("@")) {
                        throw ApiException.field(key, "email");
                    }
                    b.setEmail(email);
                }
                case "website" -> b.setWebsite(text(value, 200, key));
                case "mapsUrl" -> b.setMapsUrl(text(value, 500, key));
                case "address" -> b.setAddress(text(value, 255, key));
                case "city" -> b.setCity(text(value, 80, key));
                case "district" -> b.setDistrict(text(value, 80, key));
                case "idCode" -> b.setIdCode(text(value, 40, key));
                case "visitHours" -> b.setVisitHours(text(value, 120, key));
                case "menuChange" -> b.setMenuChange(text(value, 200, key));
                case "notes" -> b.setNotes(text(value, 20_000, key));
                case "competitorNotes" -> b.setCompetitorNotes(text(value, 20_000, key));
                case "branches" -> b.setBranches(number(value, 0, 10_000, key));
                case "reorderDays" -> b.setReorderDays(number(value, 1, 365, key));
                case "typeId" -> {
                    Long typeId = identifier(value, key);
                    b.setType(typeId == null ? null : types.findById(typeId).orElseThrow(() -> ApiException.field(key, "invalid")));
                }
                case "sheetId" -> {
                    Long sheetId = identifier(value, key);
                    b.setSheet(sheetId == null ? null : sheets.findById(sheetId).orElseThrow(() -> ApiException.field(key, "invalid")));
                }
                case "priority" -> b.setPriority(choice(Priority.class, value, key));
                case "switchOpenness" -> b.setSwitchOpenness(choice(Openness.class, value, key));
                case "priceSensitivity" -> b.setPriceSensitivity(choice(PriceSensitivity.class, value, key));
                case "satisfaction" -> b.setSatisfaction(choice(Satisfaction.class, value, key));
                case "status" -> applyStatus(b, choice(BusinessStatus.class, value, key), user, null);
                case "assignedToId" -> {
                    Long target = identifier(value, key);
                    boolean claiming = b.getAssignedTo() == null && user.getId().equals(target);
                    if (!user.isSupervisor() && !claiming) {
                        throw ApiException.forbidden();
                    }
                    b.setAssignedTo(target == null ? null : activeUser(target));
                }
                default -> {
                    if (!key.startsWith("custom.")) {
                        throw ApiException.field(key, "invalid");
                    }
                    Long fieldId;
                    try {
                        fieldId = Long.valueOf(key.substring("custom.".length()));
                    } catch (NumberFormatException ex) {
                        throw ApiException.field(key, "invalid");
                    }
                    setCustomValue(b, fieldId, text(value, 20_000, key));
                }
            }
            changed.add(key);
        }
        if (!changed.isEmpty()) {
            b.setUpdatedAt(Instant.now());
            audit.record(id, "Business", id, "EDITED", String.join(", ", changed), user);
            businesses.saveAndFlush(b);
        }
        return gridRows(List.of(b), user).get(0);
    }

    /** The same change for many rows. Rows the user may not edit are skipped and counted. */
    @Transactional
    public BulkResult bulk(BulkRequest r, User user) {
        if (r.ids() == null || r.ids().isEmpty()) {
            return new BulkResult(0, 0);
        }
        if (r.ids().size() > 5000) {
            throw ApiException.field("ids", "length");
        }
        boolean supervisorOnly = r.assignedToId() != null || Boolean.TRUE.equals(r.unassign()) || r.archived() != null;
        if (supervisorOnly && !user.isSupervisor()) {
            throw ApiException.forbidden();
        }
        var sheet = r.sheetId() == null ? null : sheets.findById(r.sheetId()).orElseThrow(() -> ApiException.field("sheetId", "invalid"));
        User assignee = r.assignedToId() == null ? null : activeUser(r.assignedToId());
        int updated = 0;
        int skipped = 0;
        for (Business b : businesses.findAllById(r.ids())) {
            if (!CurrentUser.canEdit(user, b)) {
                skipped++;
                continue;
            }
            if (sheet != null) {
                b.setSheet(sheet);
            } else if (Boolean.TRUE.equals(r.clearSheet())) {
                b.setSheet(null);
            }
            if (r.status() != null) {
                applyStatus(b, r.status(), user, null);
            }
            if (r.priority() != null) {
                b.setPriority(r.priority());
            }
            if (assignee != null) {
                b.setAssignedTo(assignee);
            } else if (Boolean.TRUE.equals(r.unassign())) {
                b.setAssignedTo(null);
            }
            if (r.archived() != null) {
                b.setArchived(r.archived());
            }
            b.setUpdatedAt(Instant.now());
            updated++;
        }
        audit.record(null, "Business", null, "BULK", updated + " businesses changed" + (skipped > 0 ? ", " + skipped + " skipped" : ""), user);
        return new BulkResult(updated, skipped);
    }

    // ------------------------------------------------------------ custom fields

    /** Sets one custom value; blank removes it. */
    public void setCustomValue(Business b, Long fieldId, String value) {
        CustomField field = customFields.findById(fieldId).orElseThrow(() -> ApiException.field("custom." + fieldId, "invalid"));
        Optional<BusinessFieldValue> existing = fieldValues.findByBusinessIdAndFieldId(b.getId(), fieldId);
        if (value == null || value.isBlank()) {
            existing.ifPresent(fieldValues::delete);
            return;
        }
        BusinessFieldValue row = existing.orElseGet(() -> new BusinessFieldValue(b, field, value));
        row.setValue(value);
        fieldValues.save(row);
    }

    public void saveCustomValues(Business b, Map<Long, String> values) {
        if (values != null) {
            values.forEach((fieldId, value) -> setCustomValue(b, fieldId, value));
        }
    }

    public Map<Long, String> customValues(Long businessId) {
        Map<Long, String> values = new LinkedHashMap<>();
        for (BusinessFieldValue value : fieldValues.findByBusinessId(businessId)) {
            values.put(value.getField().getId(), value.getValue());
        }
        return values;
    }

    private static String text(Object value, int max, String key) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > max) {
            throw ApiException.field(key, "length");
        }
        return text;
    }

    private static Integer number(Object value, int min, int max, String key) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            int number = value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString().trim());
            if (number < min || number > max) {
                throw ApiException.field(key, "range");
            }
            return number;
        } catch (NumberFormatException ex) {
            throw ApiException.field(key, "invalid");
        }
    }

    private static Long identifier(Object value, String key) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return value instanceof Number n ? n.longValue() : Long.valueOf(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw ApiException.field(key, "invalid");
        }
    }

    private static <E extends Enum<E>> E choice(Class<E> type, Object value, String key) {
        if (value == null || value.toString().isBlank()) {
            throw ApiException.field(key, "required");
        }
        try {
            return Enum.valueOf(type, value.toString().trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.field(key, "invalid");
        }
    }

    // ================================================================== helpers

    public Business load(Long id) {
        return businesses.findById(id).orElseThrow(ApiException::notFound);
    }

    public LocalDate today() {
        return LocalDate.now(properties.zoneId());
    }

    private User activeUser(Long id) {
        return users.findById(id).filter(User::isActive).orElseThrow(() -> ApiException.field("assignedToId", "invalid"));
    }

    private ProductCategory category(Long id) {
        return categories.findById(id).orElseThrow(() -> ApiException.field("categoryId", "invalid"));
    }

    private Flavor flavor(Long id) {
        return flavors.findById(id).orElseThrow(() -> ApiException.field("flavorIds", "invalid"));
    }

    private void apply(Business b, BusinessRequest r) {
        b.setName(r.name().trim());
        b.setType(r.typeId() == null ? null : types.findById(r.typeId()).orElseThrow(() -> ApiException.field("typeId", "invalid")));
        b.setPriority(r.priority() == null ? Priority.NORMAL : r.priority());
        b.setAddress(Text.blankToNull(r.address()));
        b.setCity(Text.blankToNull(r.city()));
        b.setDistrict(Text.blankToNull(r.district()));
        b.setPhone(Text.blankToNull(r.phone()));
        b.setEmail(Text.blankToNull(r.email()));
        b.setWebsite(Text.blankToNull(r.website()));
        b.setMapsUrl(Text.blankToNull(r.mapsUrl()));
        b.setLatitude(r.latitude());
        b.setLongitude(r.longitude());
        b.setIdCode(Text.blankToNull(r.idCode()));
        b.setLegalName(Text.blankToNull(r.legalName()));
        b.setBranches(r.branches());
        b.setVisitHours(Text.blankToNull(r.visitHours()));
        b.setNotes(Text.blankToNull(r.notes()));
        b.setMenuChange(Text.blankToNull(r.menuChange()));
        b.setSwitchOpenness(r.switchOpenness() == null ? Openness.UNKNOWN : r.switchOpenness());
        b.setPriceSensitivity(r.priceSensitivity() == null ? PriceSensitivity.UNKNOWN : r.priceSensitivity());
        b.setSatisfaction(r.satisfaction() == null ? Satisfaction.UNKNOWN : r.satisfaction());
        b.setCompetitorNotes(Text.blankToNull(r.competitorNotes()));
        b.setReorderDays(r.reorderDays());
        b.setSheet(r.sheetId() == null ? null
                : sheets.findById(r.sheetId()).orElseThrow(() -> ApiException.field("sheetId", "invalid")));
        if (r.drinkTypes() != null) {
            b.getDrinkTypes().clear();
            b.getDrinkTypes().addAll(r.drinkTypes());
        }
    }

    private static List<String> changedFields(Business b, BusinessRequest r) {
        List<String> changed = new ArrayList<>();
        compare(changed, "name", b.getName(), r.name());
        compare(changed, "address", b.getAddress(), r.address());
        compare(changed, "phone", b.getPhone(), r.phone());
        compare(changed, "district", b.getDistrict(), r.district());
        compare(changed, "city", b.getCity(), r.city());
        compare(changed, "idCode", b.getIdCode(), r.idCode());
        compare(changed, "legalName", b.getLegalName(), r.legalName());
        compare(changed, "visitHours", b.getVisitHours(), r.visitHours());
        if (!Objects.equals(b.getBranches(), r.branches())) {
            changed.add("branches");
        }
        compare(changed, "email", b.getEmail(), r.email());
        compare(changed, "notes", b.getNotes(), r.notes());
        compare(changed, "competitorNotes", b.getCompetitorNotes(), r.competitorNotes());
        if (!Objects.equals(b.getType() == null ? null : b.getType().getId(), r.typeId())) {
            changed.add("type");
        }
        if (!Objects.equals(b.getSheet() == null ? null : b.getSheet().getId(), r.sheetId())) {
            changed.add("sheet");
        }
        if (r.priority() != null && r.priority() != b.getPriority()) {
            changed.add("priority");
        }
        if (r.drinkTypes() != null && !new HashSet<>(r.drinkTypes()).equals(new HashSet<>(b.getDrinkTypes()))) {
            changed.add("drinkTypes");
        }
        return changed;
    }

    private static void compare(List<String> changed, String field, String before, String after) {
        if (!Objects.equals(Text.blankToNull(before), Text.blankToNull(after))) {
            changed.add(field);
        }
    }

    private static void applyContact(Contact contact, ContactRequest r) {
        contact.setName(r.name().trim());
        contact.setRoleTitle(Text.blankToNull(r.roleTitle()));
        contact.setPhone(Text.blankToNull(r.phone()));
        contact.setEmail(Text.blankToNull(r.email()));
        contact.setPreferredChannel(r.preferredChannel() == null ? ContactChannel.ANY : r.preferredChannel());
        contact.setDecisionMaker(Boolean.TRUE.equals(r.decisionMaker()));
        contact.setNotes(Text.blankToNull(r.notes()));
    }

    private static Sort sortOf(String sort) {
        Sort primary = switch (sort == null ? "" : sort) {
            case "name" -> Sort.by(Sort.Direction.ASC, "name");
            case "lastContact" -> Sort.by(Sort.Direction.ASC, "lastContactAt");
            case "created" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "lastPurchase" -> Sort.by(Sort.Direction.DESC, "lastPurchaseDate");
            default -> Sort.by(Sort.Direction.DESC, "updatedAt");
        };
        return primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private Specification<Business> spec(BusinessFilter f) {
        Instant now = Instant.now();
        Instant endOfToday = today().plusDays(1).atStartOfDay(properties.zoneId()).toInstant();
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("archived"), f.archived()));

            String q = Text.blankToNull(f.q());
            if (q != null) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                Subquery<Long> byContact = query.subquery(Long.class);
                Root<Contact> contact = byContact.from(Contact.class);
                byContact.select(contact.get("business").get("id"))
                        .where(cb.or(cb.like(cb.lower(contact.get("name")), like), cb.like(contact.get("phone"), like)));
                all.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("address")), like),
                        cb.like(root.get("phone"), like),
                        cb.like(cb.lower(root.get("idCode")), like),
                        cb.like(cb.lower(root.get("district")), like),
                        root.get("id").in(byContact)));
            }
            if (f.statuses() != null && !f.statuses().isEmpty()) {
                all.add(root.get("status").in(f.statuses()));
            }
            if (f.typeId() != null) {
                all.add(cb.equal(root.get("type").get("id"), f.typeId()));
            }
            if (Text.blankToNull(f.district()) != null) {
                all.add(cb.equal(cb.lower(root.get("district")), f.district().trim().toLowerCase(Locale.ROOT)));
            }
            if (Text.blankToNull(f.city()) != null) {
                all.add(cb.equal(cb.lower(root.get("city")), f.city().trim().toLowerCase(Locale.ROOT)));
            }
            if (f.unassigned()) {
                all.add(cb.isNull(root.get("assignedTo")));
            } else if (f.assignedToId() != null) {
                all.add(cb.equal(root.get("assignedTo").get("id"), f.assignedToId()));
            }
            if (f.brandId() != null || f.flavorId() != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<ProductUsage> usage = sub.from(ProductUsage.class);
                List<Predicate> where = new ArrayList<>();
                if (f.brandId() != null) {
                    where.add(cb.equal(usage.get("brand").get("id"), f.brandId()));
                }
                if (f.flavorId() != null) {
                    where.add(cb.equal(usage.get("flavor").get("id"), f.flavorId()));
                }
                sub.select(usage.get("business").get("id")).where(where.toArray(Predicate[]::new));
                all.add(root.get("id").in(sub));
            }
            if (f.interestFlavorId() != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Interest> interest = sub.from(Interest.class);
                sub.select(interest.get("business").get("id")).where(
                        cb.equal(interest.get("flavor").get("id"), f.interestFlavorId()),
                        cb.notEqual(interest.get("status"), InterestStatus.NOT_INTERESTED));
                all.add(root.get("id").in(sub));
            }
            if (f.customer() != null) {
                all.add(f.customer() ? cb.greaterThan(root.get("purchaseCount"), 0) : cb.equal(root.get("purchaseCount"), 0));
            }
            if (f.notContactedDays() != null && f.notContactedDays() > 0) {
                Instant before = now.minus(f.notContactedDays(), ChronoUnit.DAYS);
                all.add(cb.or(cb.isNull(root.get("lastContactAt")), cb.lessThan(root.get("lastContactAt"), before)));
            }
            if (f.purchasedDaysAgo() != null && f.purchasedDaysAgo() > 0) {
                all.add(cb.lessThanOrEqualTo(root.<LocalDate>get("lastPurchaseDate"), today().minusDays(f.purchasedDaysAgo())));
            }
            if (f.priority() != null) {
                all.add(cb.equal(root.get("priority"), f.priority()));
            }
            if (f.sheetId() != null) {
                all.add(cb.equal(root.get("sheet").get("id"), f.sheetId()));
            }
            if (f.workbookId() != null) {
                all.add(cb.equal(root.get("sheet").get("workbook").get("id"), f.workbookId()));
            }
            if (f.unfiled()) {
                all.add(cb.isNull(root.get("sheet")));
            }
            if (f.followUpDue()) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Task> task = sub.from(Task.class);
                sub.select(task.get("business").get("id")).where(
                        cb.equal(task.get("status"), TaskStatus.OPEN),
                        cb.lessThan(task.get("dueAt"), endOfToday));
                all.add(root.get("id").in(sub));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }
}
