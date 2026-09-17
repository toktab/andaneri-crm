package ge.andaneri.crm.backup;

import ge.andaneri.crm.backup.BackupBuilder.BackupFile;
import ge.andaneri.crm.backup.BackupBuilder.BrandOut;
import ge.andaneri.crm.backup.BackupBuilder.FieldOut;
import ge.andaneri.crm.backup.BackupBuilder.NamedOut;
import ge.andaneri.crm.backup.BackupBuilder.NoteOut;
import ge.andaneri.crm.backup.BackupBuilder.ProductOut;
import ge.andaneri.crm.backup.BackupBuilder.ProjectOut;
import ge.andaneri.crm.backup.BackupBuilder.SheetOut;
import ge.andaneri.crm.backup.BackupBuilder.UserOut;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.domain.Brand;
import ge.andaneri.crm.domain.BrandRepository;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.BusinessSheet;
import ge.andaneri.crm.domain.BusinessSheetRepository;
import ge.andaneri.crm.domain.BusinessType;
import ge.andaneri.crm.domain.BusinessTypeRepository;
import ge.andaneri.crm.domain.CustomField;
import ge.andaneri.crm.domain.CustomFieldRepository;
import ge.andaneri.crm.domain.Flavor;
import ge.andaneri.crm.domain.FlavorRepository;
import ge.andaneri.crm.domain.Product;
import ge.andaneri.crm.domain.ProductCategory;
import ge.andaneri.crm.domain.ProductCategoryRepository;
import ge.andaneri.crm.domain.ProductRepository;
import ge.andaneri.crm.domain.ProductStatus;
import ge.andaneri.crm.domain.QuickNote;
import ge.andaneri.crm.domain.QuickNoteRepository;
import ge.andaneri.crm.domain.Role;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.domain.Workbook;
import ge.andaneri.crm.domain.WorkbookRepository;
import ge.andaneri.crm.io.JsonTransferService;
import ge.andaneri.crm.io.JsonTransferService.JsonFile;
import ge.andaneri.crm.io.JsonTransferService.JsonImportResult;
import ge.andaneri.crm.service.AuditService;
import ge.andaneri.crm.service.SettingsService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Putting a backup back: a bare install reads the zip and comes out as the CRM was when the backup was
 * made - the team, the catalog, the projects and sheets, the extra fields, the settings, the private
 * notes, and every business with its contacts, entries, plans (done and cancelled alike), stage history,
 * comments and orders.
 *
 * <p>Passwords are the one thing a backup does not hold. Restored accounts come back switched off with an
 * unusable password; an admin gives each a password, and only then can they sign in.
 */
@Service
public class RestoreService {

    public record RestoreResult(boolean dryRun, int users, List<String> usersNeedingPassword, int projects, int sheets,
            int customFields, int brands, int flavors, int products, int businesses, int notes, int settings) {
    }

    private final JsonMapper json;
    private final JsonTransferService jsonTransfer;
    private final UserRepository users;
    private final WorkbookRepository workbooks;
    private final BusinessSheetRepository sheets;
    private final CustomFieldRepository customFields;
    private final BusinessTypeRepository types;
    private final ProductCategoryRepository categories;
    private final BrandRepository brands;
    private final FlavorRepository flavors;
    private final ProductRepository products;
    private final BusinessRepository businesses;
    private final QuickNoteRepository notes;
    private final SettingsService settings;
    private final PasswordEncoder passwords;
    private final AuditService audit;

    public RestoreService(JsonMapper json, JsonTransferService jsonTransfer, UserRepository users, WorkbookRepository workbooks,
            BusinessSheetRepository sheets, CustomFieldRepository customFields, BusinessTypeRepository types,
            ProductCategoryRepository categories, BrandRepository brands, FlavorRepository flavors, ProductRepository products,
            BusinessRepository businesses, QuickNoteRepository notes, SettingsService settings, PasswordEncoder passwords,
            AuditService audit) {
        this.json = json;
        this.jsonTransfer = jsonTransfer;
        this.users = users;
        this.workbooks = workbooks;
        this.sheets = sheets;
        this.customFields = customFields;
        this.types = types;
        this.categories = categories;
        this.brands = brands;
        this.flavors = flavors;
        this.products = products;
        this.businesses = businesses;
        this.notes = notes;
        this.settings = settings;
        this.passwords = passwords;
        this.audit = audit;
    }

    /**
     * @param force restore even though this install already holds businesses. Without it a non-empty
     *              database is refused, so a backup is never poured on top of live data by accident.
     */
    @Transactional
    public RestoreResult restore(byte[] upload, boolean force, boolean dryRun, User by) throws IOException {
        BackupFile file = read(upload);
        if (!businesses.findAll().isEmpty() && !force) {
            throw ApiException.badRequest("NOT_EMPTY");
        }

        List<String> created = new ArrayList<>();
        for (UserOut u : list(file.users())) {
            if (u.username() == null || u.username().isBlank() || users.findByUsernameIgnoreCase(u.username()).isPresent()) {
                continue;
            }
            created.add(u.username());
            if (dryRun) {
                continue;
            }
            User user = new User();
            user.setUsername(u.username().trim());
            user.setFullName(u.fullName() == null ? u.username() : u.fullName());
            user.setPhone(u.phone());
            user.setRole(JsonTransferService.parse(Role.class, u.role(), Role.SALES));
            // No password travels in a backup: the account waits, switched off, for an admin to set one.
            user.setPasswordHash(passwords.encode(randomSecret()));
            user.setActive(false);
            user.setCreatedAt(Instant.now());
            users.save(user);
        }

        int projectCount = 0;
        int sheetCount = 0;
        for (ProjectOut p : list(file.projects())) {
            if (p.name() == null || p.name().isBlank()) {
                continue;
            }
            projectCount++;
            Workbook workbook = dryRun ? null : workbooks.findFirstByNameIgnoreCase(p.name()).orElseGet(() -> {
                Workbook fresh = new Workbook();
                fresh.setName(p.name());
                fresh.setDescription(p.description());
                fresh.setColor(p.color());
                fresh.setSourceFile(p.sourceFile());
                fresh.setSortOrder((int) workbooks.count());
                fresh.setCreatedBy(by);
                return workbooks.save(fresh);
            });
            for (SheetOut s : list(p.sheets())) {
                sheetCount++;
                if (!dryRun) {
                    sheet(s, workbook, by);
                }
            }
        }
        for (SheetOut s : list(file.looseSheets())) {
            sheetCount++;
            if (!dryRun) {
                sheet(s, null, by);
            }
        }

        int fieldCount = 0;
        for (FieldOut f : list(file.customFields())) {
            if (f.label() == null || f.label().isBlank()) {
                continue;
            }
            fieldCount++;
            if (dryRun || customFields.findFirstByLabelIgnoreCase(f.label()).isPresent()) {
                continue;
            }
            CustomField field = new CustomField();
            field.setLabel(f.label());
            field.setActive(f.active());
            field.setSortOrder((int) customFields.count());
            field.setCreatedBy(by);
            customFields.save(field);
        }

        int brandCount = 0;
        int flavorCount = 0;
        int productCount = 0;
        if (file.catalog() != null) {
            for (NamedOut t : list(file.catalog().businessTypes())) {
                if (!dryRun && types.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(name(t), name(t)).isEmpty()) {
                    BusinessType type = new BusinessType();
                    type.setNameKa(t.nameKa());
                    type.setNameEn(t.nameEn());
                    type.setActive(t.active());
                    type.setSortOrder((int) types.count());
                    types.save(type);
                }
            }
            for (NamedOut c : list(file.catalog().categories())) {
                if (!dryRun && categories.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(name(c), name(c)).isEmpty()) {
                    ProductCategory category = new ProductCategory();
                    category.setNameKa(c.nameKa());
                    category.setNameEn(c.nameEn());
                    category.setActive(c.active());
                    category.setSortOrder((int) categories.count());
                    categories.save(category);
                }
            }
            for (BrandOut b : list(file.catalog().brands())) {
                if (b.name() == null || b.name().isBlank()) {
                    continue;
                }
                brandCount++;
                if (!dryRun && brands.findByNameIgnoreCase(b.name()).isEmpty()) {
                    Brand brand = new Brand();
                    brand.setName(b.name());
                    brand.setOwn(b.own());
                    brand.setActive(b.active());
                    brands.save(brand);
                }
            }
            for (NamedOut f : list(file.catalog().flavors())) {
                if (name(f) == null || name(f).isBlank()) {
                    continue;
                }
                flavorCount++;
                if (!dryRun && flavors.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(name(f), name(f)).isEmpty()) {
                    Flavor flavor = new Flavor();
                    flavor.setNameKa(f.nameKa() == null ? f.nameEn() : f.nameKa());
                    flavor.setNameEn(f.nameEn() == null ? f.nameKa() : f.nameEn());
                    flavor.setActive(f.active());
                    flavors.save(flavor);
                }
            }
            for (ProductOut p : list(file.catalog().products())) {
                if (p.nameEn() == null && p.nameKa() == null) {
                    continue;
                }
                productCount++;
                if (dryRun) {
                    continue;
                }
                Brand brand = p.brand() == null ? null : brands.findByNameIgnoreCase(p.brand()).orElse(null);
                ProductCategory category = p.category() == null ? null : categories.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(p.category(), p.category()).orElse(null);
                if (brand == null || category == null) {
                    continue;
                }
                Product existing = products.findAll().stream()
                        .filter(x -> x.getNameEn() != null && x.getNameEn().equalsIgnoreCase(p.nameEn()))
                        .findFirst().orElseGet(Product::new);
                existing.setBrand(brand);
                existing.setCategory(category);
                existing.setSectionKa(p.sectionKa());
                existing.setSectionEn(p.sectionEn());
                existing.setNameKa(p.nameKa() == null ? p.nameEn() : p.nameKa());
                existing.setNameEn(p.nameEn() == null ? p.nameKa() : p.nameEn());
                existing.setPackSize(p.packSize());
                existing.setUnit(p.unit());
                existing.setPrice(p.price());
                existing.setJuicePercent(p.juicePercent());
                existing.setStatus(JsonTransferService.parse(ProductStatus.class, p.status(), ProductStatus.ACTIVE));
                products.save(existing);
            }
        }

        int settingCount = 0;
        if (file.settings() != null && !file.settings().isEmpty()) {
            Map<String, Integer> plain = new LinkedHashMap<>(file.settings());
            settingCount = plain.size();
            if (!dryRun) {
                settings.update(plain);
                settings.updateSecurity(plain);
            }
        }

        JsonFile crm = file.crm();
        JsonImportResult imported = crm == null ? null : jsonTransfer.importFile(crm, false, dryRun, by);

        int noteCount = 0;
        for (NoteOut n : list(file.notes())) {
            if (n.body() == null || n.body().isBlank()) {
                continue;
            }
            noteCount++;
            if (dryRun) {
                continue;
            }
            User owner = n.user() == null ? null : users.findByUsernameIgnoreCase(n.user()).orElse(null);
            QuickNote note = new QuickNote();
            note.setUser(owner == null ? by : owner);
            note.setBody(n.body());
            note.setRemindAt(n.remindAt());
            note.setDone(n.done());
            if (n.business() != null) {
                businesses.findAll().stream().filter(b -> b.getName().equalsIgnoreCase(n.business())).findFirst()
                        .ifPresent(note::setBusiness);
            }
            notes.save(note);
        }

        if (!dryRun) {
            audit.record(null, "Backup", null, "RESTORED",
                    (imported == null ? 0 : imported.created()) + " businesses, " + created.size() + " accounts", by);
        }
        return new RestoreResult(dryRun, created.size(), created, projectCount, sheetCount, fieldCount, brandCount,
                flavorCount, productCount, imported == null ? 0 : imported.created(), noteCount, settingCount);
    }

    private BusinessSheet sheet(SheetOut s, Workbook workbook, User by) {
        if (s.name() == null || s.name().isBlank()) {
            return null;
        }
        return (workbook == null ? sheets.findFirstByWorkbookIsNullAndNameIgnoreCase(s.name())
                : sheets.findFirstByWorkbookIdAndNameIgnoreCase(workbook.getId(), s.name())).orElseGet(() -> {
                    BusinessSheet fresh = new BusinessSheet();
                    fresh.setWorkbook(workbook);
                    fresh.setName(s.name());
                    fresh.setColor(s.color());
                    fresh.setSortOrder((int) (workbook == null ? sheets.count() : sheets.countByWorkbookId(workbook.getId())));
                    fresh.setCreatedBy(by);
                    return sheets.save(fresh);
                });
    }

    /** The backup file itself: a zip as downloaded, or the backup.json out of it. */
    private BackupFile read(byte[] upload) throws IOException {
        JsonNode root = json.readTree(jsonOf(upload));
        if (!BackupBuilder.FORMAT.equals(root.path("format").asString(""))) {
            throw ApiException.badRequest("WRONG_FORMAT");
        }
        return json.treeToValue(root, BackupFile.class);
    }

    private static byte[] jsonOf(byte[] upload) throws IOException {
        if (upload.length < 4 || upload[0] != 'P' || upload[1] != 'K') {
            return upload;
        }
        byte[] fallback = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(upload))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory() || !entry.getName().endsWith(".json")) {
                    continue;
                }
                byte[] content = zip.readAllBytes();
                if (entry.getName().endsWith("backup.json")) {
                    return content;
                }
                if (fallback == null) {
                    fallback = content;
                }
            }
        }
        if (fallback == null) {
            throw ApiException.badRequest("WRONG_FORMAT");
        }
        return fallback;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String name(NamedOut named) {
        return named.nameEn() == null || named.nameEn().isBlank() ? named.nameKa() : named.nameEn();
    }

    private static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }
}
