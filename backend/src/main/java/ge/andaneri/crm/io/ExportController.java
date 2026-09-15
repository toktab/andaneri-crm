package ge.andaneri.crm.io;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessFieldValue;
import ge.andaneri.crm.domain.BusinessFieldValueRepository;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.Contact;
import ge.andaneri.crm.domain.ContactRepository;
import ge.andaneri.crm.domain.CustomField;
import ge.andaneri.crm.domain.CustomFieldRepository;
import ge.andaneri.crm.domain.Interest;
import ge.andaneri.crm.domain.InterestRepository;
import ge.andaneri.crm.domain.InterestStatus;
import ge.andaneri.crm.domain.Priority;
import ge.andaneri.crm.domain.ProductUsage;
import ge.andaneri.crm.domain.ProductUsageRepository;
import ge.andaneri.crm.domain.Task;
import ge.andaneri.crm.domain.TaskRepository;
import ge.andaneri.crm.domain.TaskStatus;
import ge.andaneri.crm.domain.TaskType;
import ge.andaneri.crm.service.BusinessService;
import ge.andaneri.crm.web.BusinessQuery;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * The business list, filtered exactly like the screen, as an Excel file (one row per business, every
 * field, custom fields included) or as the CRM's JSON file with full history.
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final Map<BusinessStatus, String> STATUS_KA = Map.ofEntries(
            Map.entry(BusinessStatus.NEW, "ახალი"), Map.entry(BusinessStatus.CONTACTED, "დაკავშირებული"),
            Map.entry(BusinessStatus.INTERESTED, "დაინტერესებული"), Map.entry(BusinessStatus.MEETING, "შეხვედრა"),
            Map.entry(BusinessStatus.TESTING, "ტესტირება"), Map.entry(BusinessStatus.NEGOTIATION, "მოლაპარაკება"),
            Map.entry(BusinessStatus.CUSTOMER, "კლიენტი"), Map.entry(BusinessStatus.REPEAT_CUSTOMER, "მუდმივი კლიენტი"),
            Map.entry(BusinessStatus.FOLLOW_UP_LATER, "მოგვიანებით"), Map.entry(BusinessStatus.NOT_INTERESTED, "არ დაინტერესდა"),
            Map.entry(BusinessStatus.LOST, "დაკარგული"));
    private static final Map<TaskType, String> TASK_KA = Map.of(
            TaskType.CALL, "ზარი", TaskType.MEETING, "შეხვედრა", TaskType.VISIT, "ვიზიტი",
            TaskType.SEND_SAMPLES, "სემპლების გაგზავნა", TaskType.SEND_PRICE_LIST, "ფასების გაგზავნა",
            TaskType.FOLLOW_UP, "გადაკავშირება", TaskType.CHECK_REORDER, "ხელახალი შეკვეთა", TaskType.OTHER, "სხვა");
    private static final Map<Priority, String> PRIORITY_KA = Map.of(Priority.LOW, "დაბალი", Priority.NORMAL, "საშუალო", Priority.HIGH, "მაღალი");

    private static final List<String> HEADERS_KA = List.of("ID", "პროექტი", "ფურცელი", "სავაჭრო დასახელება", "შპს დასახელება", "ტიპი",
            "სტატუსი", "პრიორიტეტი", "მისამართი", "ქალაქი", "რაიონი", "ტელეფონი", "ელ.ფოსტა", "ვებ გვერდი", "ს.კ.", "ფილიალები",
            "ვიზიტის დრო", "პასუხისმგებელი", "კონტაქტები", "ბრენდები", "გემოები", "დაინტერესება", "ბოლო კონტაქტი", "ბოლო შეკვეთა",
            "შეკვეთები", "შემდეგი ნაბიჯი", "შემდეგი ნაბიჯის თარიღი", "შენიშვნები");
    private static final List<String> HEADERS_EN = List.of("ID", "Project", "Sheet", "Name", "Legal name", "Type", "Status", "Priority",
            "Address", "City", "District", "Phone", "Email", "Website", "ID code", "Branches", "Visit hours", "Assigned to",
            "Contacts", "Brands", "Flavors", "Interested in", "Last contact", "Last order", "Orders", "Next step", "Next step date", "Notes");

    private final BusinessService businessService;
    private final ContactRepository contacts;
    private final ProductUsageRepository usages;
    private final InterestRepository interests;
    private final TaskRepository tasks;
    private final CustomFieldRepository customFields;
    private final BusinessFieldValueRepository fieldValues;
    private final CurrentUser currentUser;
    private final CrmProperties properties;
    private final JsonTransferService jsonTransfer;
    private final JsonMapper json;

    public ExportController(BusinessService businessService, ContactRepository contacts, ProductUsageRepository usages,
            InterestRepository interests, TaskRepository tasks, CustomFieldRepository customFields,
            BusinessFieldValueRepository fieldValues, CurrentUser currentUser, CrmProperties properties,
            JsonTransferService jsonTransfer, JsonMapper json) {
        this.businessService = businessService;
        this.contacts = contacts;
        this.usages = usages;
        this.interests = interests;
        this.tasks = tasks;
        this.customFields = customFields;
        this.fieldValues = fieldValues;
        this.currentUser = currentUser;
        this.properties = properties;
        this.jsonTransfer = jsonTransfer;
        this.json = json;
    }

    @GetMapping("/businesses")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> businesses(@ModelAttribute BusinessQuery query,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "ka") String lang,
            @RequestParam(defaultValue = "xlsx") String format) throws IOException {
        currentUser.require();
        boolean ka = !"en".equals(lang);
        List<Business> rows = businessService.findAll(query.toFilter(), sort);

        // The full-history JSON file: everything about each business, re-importable.
        if ("json".equals(format)) {
            byte[] body = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(jsonTransfer.export(rows));
            return ReportExportController.file(body, "andaneri-crm-" + LocalDate.now(properties.zoneId()) + ".json",
                    MediaType.APPLICATION_JSON);
        }

        Map<Long, List<Contact>> contactsBy = new HashMap<>();
        Map<Long, List<ProductUsage>> usagesBy = new HashMap<>();
        Map<Long, List<Interest>> interestsBy = new HashMap<>();
        Map<Long, Task> nextBy = new HashMap<>();
        Map<Long, Map<Long, String>> customBy = new HashMap<>();
        List<Long> ids = rows.stream().map(Business::getId).toList();
        for (int from = 0; from < ids.size(); from += 500) {
            List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + 500));
            contacts.findByBusinessIdIn(chunk).forEach(c -> contactsBy.computeIfAbsent(c.getBusiness().getId(), k -> new ArrayList<>()).add(c));
            usages.findForBusinesses(chunk).forEach(u -> usagesBy.computeIfAbsent(u.getBusiness().getId(), k -> new ArrayList<>()).add(u));
            interests.findForBusinesses(chunk).forEach(i -> interestsBy.computeIfAbsent(i.getBusiness().getId(), k -> new ArrayList<>()).add(i));
            tasks.findForBusinesses(chunk, TaskStatus.OPEN).forEach(t -> nextBy.putIfAbsent(t.getBusiness().getId(), t));
            for (BusinessFieldValue value : fieldValues.findForBusinesses(chunk)) {
                customBy.computeIfAbsent(value.getBusiness().getId(), k -> new HashMap<>()).put(value.getField().getId(), value.getValue());
            }
        }
        List<CustomField> fields = customFields.findAllByOrderBySortOrderAscIdAsc().stream().filter(CustomField::isActive).toList();
        List<String> headers = new ArrayList<>(ka ? HEADERS_KA : HEADERS_EN);
        fields.forEach(field -> headers.add(field.getLabel()));

        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(properties.zoneId());
        // Streaming workbook: thousands of rows without holding them all in memory.
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(ka ? "ბიზნესები" : "Businesses");
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row head = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                head.createCell(c).setCellValue(headers.get(c));
                head.getCell(c).setCellStyle(bold);
                sheet.setColumnWidth(c, Math.min(60, Math.max(10, headers.get(c).length() + 6)) * 256);
            }
            sheet.createFreezePane(4, 1);

            int r = 1;
            for (Business b : rows) {
                List<ProductUsage> used = usagesBy.getOrDefault(b.getId(), List.of());
                Task next = nextBy.get(b.getId());
                var businessSheet = b.getSheet();
                List<Object> values = new ArrayList<>(List.of(
                        b.getId(),
                        businessSheet == null || businessSheet.getWorkbook() == null ? "" : businessSheet.getWorkbook().getName(),
                        businessSheet == null ? "" : businessSheet.getName(),
                        b.getName()));
                values.add(b.getLegalName());
                values.add(b.getType() == null ? null : ka ? b.getType().getNameKa() : b.getType().getNameEn());
                values.add(ka ? STATUS_KA.get(b.getStatus()) : humanize(b.getStatus().name()));
                values.add(ka ? PRIORITY_KA.get(b.getPriority()) : humanize(b.getPriority().name()));
                values.add(b.getAddress());
                values.add(b.getCity());
                values.add(b.getDistrict());
                values.add(b.getPhone());
                values.add(b.getEmail());
                values.add(b.getWebsite());
                values.add(b.getIdCode());
                values.add(b.getBranches());
                values.add(b.getVisitHours());
                values.add(b.getAssignedTo() == null ? null : b.getAssignedTo().getFullName());
                values.add(contactsBy.getOrDefault(b.getId(), List.of()).stream()
                        .map(c -> join(" ", c.getName(), c.getRoleTitle() == null ? null : "(" + c.getRoleTitle() + ")", c.getPhone()))
                        .collect(Collectors.joining("; ")));
                values.add(distinct(used, u -> u.getBrand() == null ? null : u.getBrand().getName()));
                values.add(distinct(used, u -> u.getFlavor() == null ? null : ka ? u.getFlavor().getNameKa() : u.getFlavor().getNameEn()));
                values.add(distinct(interestsBy.getOrDefault(b.getId(), List.of()).stream()
                                .filter(i -> i.getStatus() != InterestStatus.NOT_INTERESTED).toList(),
                        i -> i.getFlavor() != null ? (ka ? i.getFlavor().getNameKa() : i.getFlavor().getNameEn())
                                : i.getProduct() == null ? null : ka ? i.getProduct().getNameKa() : i.getProduct().getNameEn()));
                values.add(b.getLastContactAt() == null ? null : dateTime.format(b.getLastContactAt()));
                values.add(b.getLastPurchaseDate());
                values.add(b.getPurchaseCount());
                values.add(next == null ? null : join(" - ", ka ? TASK_KA.get(next.getType()) : humanize(next.getType().name()), next.getTitle()));
                values.add(next == null ? null : dateTime.format(next.getDueAt()));
                values.add(b.getNotes());
                Map<Long, String> custom = customBy.getOrDefault(b.getId(), Map.of());
                fields.forEach(field -> values.add(custom.get(field.getId())));

                Row row = sheet.createRow(r++);
                for (int c = 0; c < values.size(); c++) {
                    Object value = values.get(c);
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else if (value != null && !value.toString().isBlank()) {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            String fileName = (ka ? "ბიზნესები-" : "businesses-") + LocalDate.now(properties.zoneId()) + ".xlsx";
            return ReportExportController.file(out.toByteArray(), fileName,
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        }
    }

    private static <T> String distinct(List<T> items, Function<T, String> name) {
        return items.stream().map(name).filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new)).stream().collect(Collectors.joining(", "));
    }

    private static String join(String separator, String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                present.add(part);
            }
        }
        return String.join(separator, present);
    }

    private static String humanize(String enumName) {
        String lower = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
