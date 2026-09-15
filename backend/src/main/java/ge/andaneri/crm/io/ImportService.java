package ge.andaneri.crm.io;

import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.Activity;
import ge.andaneri.crm.domain.ActivityRepository;
import ge.andaneri.crm.domain.Brand;
import ge.andaneri.crm.domain.BrandRepository;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.BusinessType;
import ge.andaneri.crm.domain.BusinessTypeRepository;
import ge.andaneri.crm.domain.Comment;
import ge.andaneri.crm.domain.CommentRepository;
import ge.andaneri.crm.domain.Contact;
import ge.andaneri.crm.domain.ContactRepository;
import ge.andaneri.crm.domain.Flavor;
import ge.andaneri.crm.domain.FlavorRepository;
import ge.andaneri.crm.domain.InterestReason;
import ge.andaneri.crm.domain.InterestStatus;
import ge.andaneri.crm.domain.ProductCategory;
import ge.andaneri.crm.domain.ProductCategoryRepository;
import ge.andaneri.crm.domain.StatusChange;
import ge.andaneri.crm.domain.StatusChangeRepository;
import ge.andaneri.crm.domain.Task;
import ge.andaneri.crm.domain.TaskRepository;
import ge.andaneri.crm.domain.TaskType;
import ge.andaneri.crm.domain.UsageAnswer;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.io.ImportParser.Field;
import ge.andaneri.crm.io.ImportParser.ParsedContact;
import ge.andaneri.crm.io.ImportParser.ParsedHistory;
import ge.andaneri.crm.io.ImportParser.ParsedRow;
import ge.andaneri.crm.io.ImportParser.Vocabulary;
import ge.andaneri.crm.service.AuditService;
import ge.andaneri.crm.service.BusinessService;
import ge.andaneri.crm.service.DuplicateIndex;
import ge.andaneri.crm.web.BusinessDtos.DuplicateDto;
import ge.andaneri.crm.web.BusinessDtos.InterestRequest;
import ge.andaneri.crm.web.BusinessDtos.UsageRequest;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Brings the old spreadsheet in, in three steps the frontend walks through: read the file (every
 * sheet, with a guessed column mapping), preview what each row would become, then create it all
 * in one transaction. The rows travel with each request, so nothing is stored between steps.
 */
@Service
public class ImportService {

    private static final int MAX_ROWS = 20_000;

    public record SheetDto(String name, List<String> headers, List<List<String>> rows, Map<Integer, String> mapping) {
    }

    public record ParsedFile(String fileName, List<SheetDto> sheets) {
    }

    /** Both switches default to on when left out. */
    public record ImportOptions(Long assignedToId, Long typeId, String city, String district,
            Boolean skipDuplicates, Boolean createFollowUps) {
    }

    /**
     * One sheet of a file. {@code mapping}: column index to a field name or "CUSTOM:&lt;id&gt;". With a
     * {@code sheetName} the rows are filed under that sheet, inside the project {@code workbookId} or the
     * project named {@code workbookName} (created if new), or outside any project when both are empty.
     */
    public record ImportRequest(@NotNull List<String> headers, @NotNull List<List<String>> rows,
            @NotNull Map<Integer, String> mapping, ImportOptions options, String source,
            Long workbookId, String workbookName, String sheetName) {
    }

    public record PreviewContact(String name, String roleTitle, String phone) {
    }

    public record PreviewRow(int index, String name, String address, String phone, String idCode,
            List<PreviewContact> contacts, UsageAnswer usesSyrup, List<String> brands, List<String> flavors,
            List<String> sampleFlavors, int historyCount, BusinessStatus status, String nextStep,
            LocalDate nextStepDate, DuplicateDto duplicate, String error) {
    }

    /** {@code ready} includes the possible duplicates, which are imported; {@code duplicates} counts only certain ones. */
    public record Preview(int total, int ready, int duplicates, int possibleDuplicates, int invalid, int customers,
            List<PreviewRow> rows) {
    }

    public record ImportResult(int created, int skippedDuplicates, int skippedInvalid, int contacts, int activities,
            int comments, int tasks) {
    }

    private final BusinessRepository businesses;
    private final BusinessTypeRepository types;
    private final BrandRepository brands;
    private final FlavorRepository flavors;
    private final ProductCategoryRepository categories;
    private final ContactRepository contacts;
    private final ActivityRepository activities;
    private final CommentRepository comments;
    private final TaskRepository tasks;
    private final StatusChangeRepository statusChanges;
    private final UserRepository users;
    private final BusinessService businessService;
    private final AuditService audit;
    private final CrmProperties properties;
    private final ge.andaneri.crm.service.WorkspaceService workspace;
    private final ge.andaneri.crm.domain.WorkbookRepository workbookRepository;
    private final ge.andaneri.crm.domain.CustomFieldRepository customFields;

    public ImportService(BusinessRepository businesses, BusinessTypeRepository types, BrandRepository brands,
            FlavorRepository flavors, ProductCategoryRepository categories, ContactRepository contacts,
            ActivityRepository activities, CommentRepository comments, TaskRepository tasks,
            StatusChangeRepository statusChanges, UserRepository users, BusinessService businessService,
            AuditService audit, CrmProperties properties, ge.andaneri.crm.service.WorkspaceService workspace,
            ge.andaneri.crm.domain.WorkbookRepository workbookRepository, ge.andaneri.crm.domain.CustomFieldRepository customFields) {
        this.workspace = workspace;
        this.workbookRepository = workbookRepository;
        this.customFields = customFields;
        this.businesses = businesses;
        this.types = types;
        this.brands = brands;
        this.flavors = flavors;
        this.categories = categories;
        this.contacts = contacts;
        this.activities = activities;
        this.comments = comments;
        this.tasks = tasks;
        this.statusChanges = statusChanges;
        this.users = users;
        this.businessService = businessService;
        this.audit = audit;
        this.properties = properties;
    }

    // ================================================================== 1. read the file

    public ParsedFile read(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "import" : file.getOriginalFilename();
        try (InputStream in = file.getInputStream()) {
            List<SheetDto> sheets = name.toLowerCase(Locale.ROOT).endsWith(".csv") ? List.of(readCsv(in)) : readWorkbook(in);
            return new ParsedFile(name, sheets);
        } catch (IOException | RuntimeException ex) {
            throw ApiException.badRequest("UNREADABLE_FILE");
        }
    }

    private List<SheetDto> readWorkbook(InputStream in) throws IOException {
        List<SheetDto> sheets = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (Sheet sheet : workbook) {
                List<List<String>> all = new ArrayList<>();
                for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum() && all.size() < MAX_ROWS; r++) {
                    Row row = sheet.getRow(r);
                    List<String> cells = new ArrayList<>();
                    if (row != null) {
                        for (int c = 0; c < row.getLastCellNum(); c++) {
                            Cell cell = row.getCell(c);
                            cells.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim());
                        }
                    }
                    all.add(cells);
                }
                SheetDto dto = toSheet(sheet.getSheetName(), all);
                if (dto != null) {
                    sheets.add(dto);
                }
            }
        }
        return sheets;
    }

    private SheetDto readCsv(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String firstLine = text.lines().findFirst().orElse("");
        char delimiter = firstLine.chars().filter(ch -> ch == ';').count() > firstLine.chars().filter(ch -> ch == ',').count() ? ';' : ',';
        List<List<String>> all = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)) {
            for (CSVRecord record : CSVFormat.DEFAULT.builder().setDelimiter(delimiter).get().parse(reader)) {
                List<String> cells = new ArrayList<>();
                record.forEach(value -> cells.add(value == null ? "" : value.trim()));
                all.add(cells);
                if (all.size() >= MAX_ROWS) {
                    break;
                }
            }
        }
        SheetDto sheet = toSheet("CSV", all);
        if (sheet == null) {
            throw ApiException.badRequest("EMPTY_FILE");
        }
        return sheet;
    }

    /** The header is the first of the top ten rows with at least three filled cells; empty rows are dropped. */
    private static SheetDto toSheet(String name, List<List<String>> all) {
        int headerAt = -1;
        for (int i = 0; i < Math.min(10, all.size()); i++) {
            if (all.get(i).stream().filter(cell -> !cell.isBlank()).count() >= 3) {
                headerAt = i;
                break;
            }
        }
        if (headerAt < 0) {
            return null;
        }
        List<String> headers = new ArrayList<>(all.get(headerAt));
        while (!headers.isEmpty() && headers.get(headers.size() - 1).isBlank()) {
            headers.remove(headers.size() - 1);
        }
        List<List<String>> rows = new ArrayList<>();
        for (List<String> row : all.subList(headerAt + 1, all.size())) {
            if (row.stream().anyMatch(cell -> !cell.isBlank())) {
                rows.add(row.size() > headers.size() ? row.subList(0, headers.size()) : row);
            }
        }
        return new SheetDto(name, headers, rows, ImportParser.guessMapping(headers));
    }

    // ================================================================== 2. preview

    @Transactional(readOnly = true)
    public Preview preview(ImportRequest request) {
        Vocabulary vocabulary = vocabulary();
        DuplicateIndex index = DuplicateIndex.of(businesses.findByArchivedFalse());
        LocalDate today = LocalDate.now(properties.zoneId());
        List<PreviewRow> rows = new ArrayList<>();
        int ready = 0;
        int duplicates = 0;
        int possible = 0;
        int invalid = 0;
        int customers = 0;
        for (int i = 0; i < request.rows().size(); i++) {
            ParsedRow row = ImportParser.parse(i, request.headers(), request.rows().get(i), request.mapping(), vocabulary, today);
            DuplicateDto duplicate = null;
            if (row.error() == null) {
                duplicate = index.match(row.name(), row.address(), row.phone(), row.idCode(), null).orElse(null);
                index.add(null, row.name(), row.address(), row.phone(), row.idCode());
            }
            if (row.error() != null) {
                invalid++;
            } else if (duplicate != null && duplicate.strong()) {
                duplicates++;
            } else {
                ready++;
                if (duplicate != null) {
                    possible++;
                }
            }
            if (row.customer()) {
                customers++;
            }
            rows.add(new PreviewRow(i, row.name(), row.address(), row.phone(), row.idCode(),
                    row.contacts().stream().map(c -> new PreviewContact(c.name(), c.roleTitle(), c.phone())).toList(),
                    row.usesSyrup(), row.brands(), row.flavors(), row.sampleFlavors(), row.history().size(), row.status(),
                    row.nextStep(), row.nextStepDate(), duplicate, row.error()));
        }
        return new Preview(rows.size(), ready, duplicates, possible, invalid, customers, rows);
    }

    // ================================================================== 3. commit

    @Transactional
    public ImportResult commit(ImportRequest request, User user) {
        ImportOptions options = request.options() == null
                ? new ImportOptions(null, null, null, null, true, true) : request.options();
        ZoneId zone = properties.zoneId();
        LocalDate today = LocalDate.now(zone);
        Instant now = Instant.now();
        Vocabulary vocabulary = vocabulary();
        DuplicateIndex index = DuplicateIndex.of(businesses.findByArchivedFalse());

        User assignee = user.isSupervisor()
                ? (options.assignedToId() == null ? null
                        : users.findById(options.assignedToId()).filter(User::isActive)
                                .orElseThrow(() -> ApiException.field("assignedToId", "invalid")))
                : user;
        BusinessType defaultType = options.typeId() == null ? null
                : types.findById(options.typeId()).orElseThrow(() -> ApiException.field("typeId", "invalid"));
        ProductCategory syrup = categories.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase("სიროფი", "Syrup")
                .orElseThrow(() -> new IllegalStateException("The Syrup category is missing"));
        Map<String, Flavor> flavorByName = new HashMap<>();
        flavors.findAll().forEach(f -> flavorByName.put(f.getNameEn(), f));
        ge.andaneri.crm.domain.BusinessSheet targetSheet = targetSheet(request, user);
        java.util.Set<Long> knownFields = customFields.findAll().stream()
                .map(ge.andaneri.crm.domain.CustomField::getId).collect(java.util.stream.Collectors.toSet());

        int created = 0;
        int skippedDuplicates = 0;
        int skippedInvalid = 0;
        int contactCount = 0;
        int activityCount = 0;
        int commentCount = 0;
        int taskCount = 0;

        for (int i = 0; i < request.rows().size(); i++) {
            ParsedRow row = ImportParser.parse(i, request.headers(), request.rows().get(i), request.mapping(), vocabulary, today);
            if (row.error() != null) {
                skippedInvalid++;
                continue;
            }
            // Only a certain duplicate is skipped; a shared phone or ID code is often a second branch.
            boolean duplicate = index.match(row.name(), row.address(), row.phone(), row.idCode(), null)
                    .map(DuplicateDto::strong).orElse(false);
            index.add(null, row.name(), row.address(), row.phone(), row.idCode());
            if (duplicate && !Boolean.FALSE.equals(options.skipDuplicates())) {
                skippedDuplicates++;
                continue;
            }

            Business b = new Business();
            b.setName(ImportParser.truncate(row.name(), 160));
            b.setLegalName(ImportParser.truncate(row.legalName(), 200));
            b.setAddress(ImportParser.truncate(row.address(), 255));
            b.setCity(ImportParser.truncate(row.city() != null ? row.city() : blank(options.city()), 80));
            b.setDistrict(ImportParser.truncate(row.district() != null ? row.district() : blank(options.district()), 80));
            b.setType(row.typeText() == null ? defaultType
                    : types.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(row.typeText(), row.typeText()).orElse(defaultType));
            b.setPhone(ImportParser.truncate(row.phone(), 60));
            b.setEmail(row.email());
            b.setWebsite(ImportParser.truncate(row.website(), 200));
            b.setIdCode(row.idCode());
            b.setBranches(row.branches());
            b.setSheet(targetSheet);
            b.setStatus(row.status());
            b.setCreatedBy(user);
            b.setAssignedTo(assignee);
            b.setCreatedAt(now);
            b.setUpdatedAt(now);
            b.setLastContactAt(row.history().stream()
                    .filter(h -> !h.plainComment())
                    .map(h -> h.date() == null ? now : noon(h.date(), zone))
                    .max(Instant::compareTo).orElse(null));
            b = businesses.save(b);
            statusChanges.save(new StatusChange(b, null, b.getStatus(), user, "Excel"));
            created++;

            for (ParsedContact person : row.contacts()) {
                Contact contact = new Contact();
                contact.setBusiness(b);
                contact.setName(person.name());
                contact.setRoleTitle(person.roleTitle());
                contact.setPhone(person.phone());
                contact.setEmail(person.email());
                contact.setDecisionMaker(person.decisionMaker());
                contacts.save(contact);
                contactCount++;
            }

            for (Map.Entry<Long, String> custom : row.customValues().entrySet()) {
                if (knownFields.contains(custom.getKey())) {
                    businessService.setCustomValue(b, custom.getKey(), custom.getValue());
                }
            }

            if (row.usesSyrup() != UsageAnswer.UNKNOWN) {
                businessService.upsertCategoryAnswer(b, syrup.getId(), row.usesSyrup(), null);
            }
            List<Long> flavorIds = row.flavors().stream().map(flavorByName::get).filter(Objects::nonNull).map(Flavor::getId).toList();
            List<Brand> usedBrands = row.brands().stream().map(this::brand).toList();
            if (usedBrands.size() == 1) {
                businessService.addUsagesTo(b, new UsageRequest(syrup.getId(), usedBrands.get(0).getId(), flavorIds, null, null, null, row.flavorText()), user);
            } else {
                // With two brands the sheet does not say which flavor is whose, so neither is guessed.
                for (Brand brand : usedBrands) {
                    businessService.addUsagesTo(b, new UsageRequest(syrup.getId(), brand.getId(), List.of(), null, null, null, null), user);
                }
                if (!flavorIds.isEmpty()) {
                    businessService.addUsagesTo(b, new UsageRequest(syrup.getId(), null, flavorIds, null, null, null, row.flavorText()), user);
                }
            }
            if (row.flavorText() != null && flavorIds.isEmpty()) {
                comments.save(comment(b, user, label(request, Field.FLAVORS) + ": " + row.flavorText(), now));
                commentCount++;
            }

            List<Long> sampleIds = row.sampleFlavors().stream().map(flavorByName::get).filter(Objects::nonNull).map(Flavor::getId).toList();
            if (!sampleIds.isEmpty()) {
                businessService.addInterestsTo(b, new InterestRequest(sampleIds, null, InterestStatus.TESTING,
                        InterestReason.GENERAL, null, row.samplesText()), user);
            }

            int n = row.history().size();
            for (int h = 0; h < n; h++) {
                ParsedHistory item = row.history().get(h);
                String text = item.label().isBlank() ? item.text() : item.label() + ": " + item.text();
                if (item.plainComment()) {
                    comments.save(comment(b, user, text, now.minusSeconds(n - h)));
                    commentCount++;
                    continue;
                }
                Activity activity = new Activity();
                activity.setBusiness(b);
                activity.setUser(user);
                activity.setType(item.type());
                activity.setResult(item.result());
                // Undated cells keep their column order, a second apart.
                activity.setOccurredAt(item.date() == null ? now.minusSeconds(n - h) : noon(item.date(), zone));
                activity.setNotes(text);
                activity.setImported(true);
                activities.save(activity);
                activityCount++;
            }

            if (row.nextStep() != null) {
                if (!Boolean.FALSE.equals(options.createFollowUps())) {
                    LocalDate due = row.nextStepDate() != null && !row.nextStepDate().isBefore(today) ? row.nextStepDate() : today;
                    Task task = new Task();
                    task.setBusiness(b);
                    task.setType(TaskType.FOLLOW_UP);
                    task.setTitle(ImportParser.truncate(row.nextStep(), 200));
                    task.setNotes(row.nextStep());
                    task.setDueAt(due.atTime(10, 0).atZone(zone).toInstant());
                    task.setAssignedTo(b.getAssignedTo() != null ? b.getAssignedTo() : user);
                    task.setCreatedBy(user);
                    tasks.save(task);
                    taskCount++;
                } else {
                    comments.save(comment(b, user, label(request, Field.NEXT_STEP) + ": " + row.nextStep(), now));
                    commentCount++;
                }
            }
            audit.record(b.getId(), "Business", b.getId(), "IMPORTED", request.source(), user);
        }
        audit.record(null, "Import", null, "COMPLETED",
                (request.source() == null ? "" : request.source() + ": ") + created + " created, "
                        + skippedDuplicates + " duplicates skipped", user);
        return new ImportResult(created, skippedDuplicates, skippedInvalid, contactCount, activityCount, commentCount, taskCount);
    }

    // ================================================================== helpers

    private Vocabulary vocabulary() {
        return new Vocabulary(
                flavors.findAll().stream().map(f -> new String[] {f.getNameKa(), f.getNameEn()}).toList(),
                brands.findAll().stream().map(Brand::getName).toList());
    }

    /** A brand named in the sheet: the existing one, or a new competitor. */
    private Brand brand(String name) {
        return brands.findByNameIgnoreCase(name).orElseGet(() -> brands.save(new Brand(name, false)));
    }

    private static Comment comment(Business b, User user, String body, Instant at) {
        Comment comment = new Comment();
        comment.setBusiness(b);
        comment.setAuthor(user);
        comment.setBody(body);
        comment.setCreatedAt(at);
        return comment;
    }

    /** The sheet the rows go into, created (with its project) if new; null when the request names no sheet. */
    private ge.andaneri.crm.domain.BusinessSheet targetSheet(ImportRequest request, User user) {
        if (request.sheetName() == null || request.sheetName().isBlank()) {
            return null;
        }
        ge.andaneri.crm.domain.Workbook project = null;
        if (request.workbookId() != null) {
            project = workbookRepository.findById(request.workbookId()).orElseThrow(() -> ApiException.field("workbookId", "invalid"));
        } else if (request.workbookName() != null && !request.workbookName().isBlank()) {
            project = workspace.workbook(request.workbookName(), request.source(), user);
        }
        return workspace.sheet(request.sheetName(), project, user);
    }

    private static String label(ImportRequest request, Field field) {
        return request.mapping().entrySet().stream()
                .filter(e -> field.name().equals(e.getValue()) && e.getKey() < request.headers().size())
                .map(e -> ImportParser.label(request.headers().get(e.getKey())))
                .findFirst().orElse("");
    }

    private static Instant noon(LocalDate date, ZoneId zone) {
        return date.atTime(12, 0).atZone(zone).toInstant();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
