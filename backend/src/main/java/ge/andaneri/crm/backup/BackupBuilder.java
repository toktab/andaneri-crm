package ge.andaneri.crm.backup;

import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.BusinessSheet;
import ge.andaneri.crm.domain.BusinessSheetRepository;
import ge.andaneri.crm.domain.BusinessTypeRepository;
import ge.andaneri.crm.domain.BrandRepository;
import ge.andaneri.crm.domain.CustomFieldRepository;
import ge.andaneri.crm.domain.FlavorRepository;
import ge.andaneri.crm.domain.ProductCategoryRepository;
import ge.andaneri.crm.domain.ProductRepository;
import ge.andaneri.crm.domain.QuickNoteRepository;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.domain.WorkbookRepository;
import ge.andaneri.crm.io.JsonTransferService;
import ge.andaneri.crm.io.JsonTransferService.JsonFile;
import ge.andaneri.crm.service.SettingsService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Everything in the CRM as one zip: backup.json (projects, sheets, extra fields, catalog, team without
 * passwords, settings, private notes, and every business with its whole history), a businesses-only file
 * in the CRM's own import format, and a README explaining both. JSON rather than Excel: it is exact,
 * quick to make, and the admin page turns it into an Excel file whenever one is wanted.
 */
@Component
public class BackupBuilder {

    public static final String FORMAT = "andaneri-crm-backup";
    public static final int VERSION = 1;

    public record Built(String fileName, byte[] zip, int businesses, int projects) {
    }

    public record BackupFile(String format, Integer version, Instant createdAt, String createdBy, Map<String, Integer> counts,
            List<ProjectOut> projects, List<SheetOut> looseSheets, List<FieldOut> customFields, CatalogOut catalog,
            List<UserOut> users, Map<String, Integer> settings, List<NoteOut> notes,
            /** The businesses with full history, in the same format as the CRM's JSON export and import. */
            JsonFile crm) {
    }

    public record ProjectOut(String name, String description, String color, String sourceFile, List<SheetOut> sheets) {
    }

    public record SheetOut(String name, String color) {
    }

    public record FieldOut(String label, boolean active) {
    }

    public record CatalogOut(List<NamedOut> businessTypes, List<NamedOut> categories, List<BrandOut> brands,
            List<NamedOut> flavors, List<ProductOut> products) {
    }

    public record NamedOut(String nameKa, String nameEn, boolean active) {
    }

    public record BrandOut(String name, boolean own, boolean active) {
    }

    public record ProductOut(String brand, String category, String sectionKa, String sectionEn, String nameKa, String nameEn,
            String packSize, String unit, BigDecimal price, Integer juicePercent, String status) {
    }

    public record UserOut(String username, String fullName, String phone, String role, boolean active) {
    }

    public record NoteOut(String user, String business, String body, Instant remindAt, boolean done, Instant createdAt) {
    }

    private final BusinessRepository businesses;
    private final WorkbookRepository workbooks;
    private final BusinessSheetRepository sheets;
    private final CustomFieldRepository customFields;
    private final BusinessTypeRepository types;
    private final ProductCategoryRepository categories;
    private final BrandRepository brands;
    private final FlavorRepository flavors;
    private final ProductRepository products;
    private final UserRepository users;
    private final QuickNoteRepository notes;
    private final SettingsService settings;
    private final JsonTransferService jsonTransfer;
    private final JsonMapper json;
    private final CrmProperties properties;

    public BackupBuilder(BusinessRepository businesses, WorkbookRepository workbooks, BusinessSheetRepository sheets,
            CustomFieldRepository customFields, BusinessTypeRepository types, ProductCategoryRepository categories,
            BrandRepository brands, FlavorRepository flavors, ProductRepository products, UserRepository users,
            QuickNoteRepository notes, SettingsService settings, JsonTransferService jsonTransfer, JsonMapper json,
            CrmProperties properties) {
        this.businesses = businesses;
        this.workbooks = workbooks;
        this.sheets = sheets;
        this.customFields = customFields;
        this.types = types;
        this.categories = categories;
        this.brands = brands;
        this.flavors = flavors;
        this.products = products;
        this.users = users;
        this.notes = notes;
        this.settings = settings;
        this.jsonTransfer = jsonTransfer;
        this.json = json;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Built build(User by, Instant now) {
        List<Business> all = businesses.findAll(Sort.by("id"));
        JsonFile crm = jsonTransfer.export(all);

        List<BusinessSheet> allSheets = sheets.findAllByOrderBySortOrderAscIdAsc();
        List<ProjectOut> projects = workbooks.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(w -> new ProjectOut(w.getName(), w.getDescription(), w.getColor(), w.getSourceFile(), allSheets.stream()
                        .filter(s -> s.getWorkbook() != null && s.getWorkbook().getId().equals(w.getId()))
                        .map(s -> new SheetOut(s.getName(), s.getColor())).toList()))
                .toList();
        List<SheetOut> loose = allSheets.stream().filter(s -> s.getWorkbook() == null).map(s -> new SheetOut(s.getName(), s.getColor())).toList();

        CatalogOut catalog = new CatalogOut(
                types.findAll(Sort.by("sortOrder", "id")).stream().map(t -> new NamedOut(t.getNameKa(), t.getNameEn(), t.isActive())).toList(),
                categories.findAll(Sort.by("sortOrder", "id")).stream().map(c -> new NamedOut(c.getNameKa(), c.getNameEn(), c.isActive())).toList(),
                brands.findAll(Sort.by("name")).stream().map(b -> new BrandOut(b.getName(), b.isOwn(), b.isActive())).toList(),
                flavors.findAll(Sort.by("nameEn")).stream().map(f -> new NamedOut(f.getNameKa(), f.getNameEn(), f.isActive())).toList(),
                products.findAll(Sort.by("sortOrder", "id")).stream().map(p -> new ProductOut(p.getBrand().getName(), p.getCategory().getNameEn(),
                        p.getSectionKa(), p.getSectionEn(), p.getNameKa(), p.getNameEn(), p.getPackSize(), p.getUnit(), p.getPrice(),
                        p.getJuicePercent(), p.getStatus().name())).toList());

        Map<String, Integer> settingValues = new LinkedHashMap<>(settings.all());
        settingValues.putAll(settings.security());

        List<NoteOut> noteOut = notes.findAll(Sort.by("id")).stream()
                .map(n -> new NoteOut(n.getUser().getUsername(), n.getBusiness() == null ? null : n.getBusiness().getName(), n.getBody(),
                        n.getRemindAt(), n.isDone(), n.getCreatedAt()))
                .toList();

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("businesses", all.size());
        counts.put("projects", projects.size());
        counts.put("sheets", allSheets.size());
        counts.put("contacts", sum(crm, b -> size(b.contacts())));
        counts.put("activities", sum(crm, b -> size(b.activities())));
        counts.put("tasks", sum(crm, b -> size(b.tasks())));
        counts.put("purchases", sum(crm, b -> size(b.purchases())));
        counts.put("comments", sum(crm, b -> size(b.comments())));
        counts.put("notes", noteOut.size());

        BackupFile file = new BackupFile(FORMAT, VERSION, now, by.getUsername(), counts, projects, loose,
                customFields.findAllByOrderBySortOrderAscIdAsc().stream().map(f -> new FieldOut(f.getLabel(), f.isActive())).toList(),
                catalog,
                users.findAllByOrderByFullNameAsc().stream().map(u -> new UserOut(u.getUsername(), u.getFullName(), u.getPhone(), u.getRole().name(), u.isActive())).toList(),
                settingValues, noteOut, crm);

        String stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(properties.zoneId()).format(now);
        String folder = "andaneri-backup-" + stamp;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            entry(zip, folder + "/README.txt", readme(file, now).getBytes(StandardCharsets.UTF_8));
            entry(zip, folder + "/backup.json", json.writerWithDefaultPrettyPrinter().writeValueAsBytes(file));
            entry(zip, folder + "/businesses-import.json", json.writerWithDefaultPrettyPrinter().writeValueAsBytes(crm));
            zip.finish();
            return new Built(folder + ".zip", bytes.toByteArray(), all.size(), projects.size());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String readme(BackupFile file, Instant now) {
        String when = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(properties.zoneId()).format(now);
        StringBuilder counts = new StringBuilder();
        file.counts().forEach((name, value) -> counts.append("  ").append(String.format("%-12s", name)).append(value).append("\r\n"));
        return """
                Andaneri CRM - backup / ბექაპი
                ================================
                Made: %s (%s) by %s

                %s
                Files
                -----
                backup.json             Everything: projects and sheets, extra fields, the product catalog,
                                        team accounts (no passwords), settings, private notes, and every
                                        business with its contacts, calls, visits, tasks, comments, orders
                                        and answers.
                businesses-import.json  Only the businesses with their full history, in the CRM's import
                                        format: Import / export - JSON import brings them back into a CRM
                                        (businesses already there are skipped).

                Open it in Excel
                ----------------
                Admin - Backups - "Convert to Excel": choose this .zip (or backup.json) and an .xlsx with
                one sheet per kind of data is downloaded.

                Format of backup.json
                ---------------------
                {
                  "format": "andaneri-crm-backup", "version": 1, "createdAt": "...", "counts": { ... },
                  "projects": [ { "name": "...", "sheets": [ { "name": "..." } ] } ],
                  "looseSheets": [ ... ], "customFields": [ { "label": "...", "active": true } ],
                  "catalog": { "businessTypes": [...], "categories": [...], "brands": [...], "flavors": [...], "products": [...] },
                  "users": [ { "username": "...", "fullName": "...", "role": "SALES", "active": true } ],
                  "settings": { "stale_days": 14, ... },
                  "notes": [ { "user": "...", "business": "...", "body": "...", "remindAt": null, "done": false } ],
                  "crm": { "format": "andaneri-crm", "version": 1,
                           "businesses": [ { "name": "...", "project": "...", "sheet": "...", "status": "CUSTOMER",
                                             "contacts": [...], "activities": [...], "tasks": [...],
                                             "comments": [...], "purchases": [...], "usages": [...],
                                             "interests": [...], "customFields": { "label": "value" } } ] }
                }
                Times are UTC (ISO 8601). Keep this file private: it holds every customer's data.
                """.formatted(when, properties.zoneId(), file.createdBy(), counts).replace("\n", "\r\n");
    }

    private static void entry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static int sum(JsonFile crm, ToIntFunction<JsonTransferService.JsonBusiness> count) {
        return crm.businesses().stream().filter(Objects::nonNull).mapToInt(count).sum();
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
