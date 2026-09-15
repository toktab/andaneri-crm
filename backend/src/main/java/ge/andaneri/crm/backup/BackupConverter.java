package ge.andaneri.crm.backup;

import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.io.JsonTransferService.JsonBusiness;
import ge.andaneri.crm.io.JsonTransferService.JsonFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a backup (the .zip, its backup.json, or a CRM JSON export) into an Excel workbook with one sheet
 * per kind of data: businesses with their extra fields, contacts, calls and visits, tasks, orders,
 * comments, and for full backups also projects, products, the team and notes.
 */
@Component
public class BackupConverter {

    private static final Map<String, String> STATUS_KA = Map.ofEntries(
            Map.entry("NEW", "ახალი"), Map.entry("CONTACTED", "დაკავშირებული"), Map.entry("INTERESTED", "დაინტერესებული"),
            Map.entry("MEETING", "შეხვედრა"), Map.entry("TESTING", "ტესტირება"), Map.entry("NEGOTIATION", "მოლაპარაკება"),
            Map.entry("CUSTOMER", "კლიენტი"), Map.entry("REPEAT_CUSTOMER", "მუდმივი კლიენტი"), Map.entry("FOLLOW_UP_LATER", "მოგვიანებით"),
            Map.entry("NOT_INTERESTED", "არ დაინტერესდა"), Map.entry("LOST", "დაკარგული"),
            Map.entry("OPEN", "ღია"), Map.entry("DONE", "შესრულებული"), Map.entry("CANCELLED", "გაუქმებული"));
    private static final Map<String, String> TYPE_KA = Map.ofEntries(
            Map.entry("CALL", "ზარი"), Map.entry("MEETING", "შეხვედრა"), Map.entry("VISIT", "ვიზიტი"), Map.entry("SAMPLES", "სემპლები"),
            Map.entry("MESSAGE", "შეტყობინება"), Map.entry("SEND_SAMPLES", "სემპლების გაგზავნა"), Map.entry("SEND_PRICE_LIST", "ფასების გაგზავნა"),
            Map.entry("FOLLOW_UP", "გადაკავშირება"), Map.entry("CHECK_REORDER", "ხელახალი შეკვეთა"), Map.entry("OTHER", "სხვა"));
    private static final Map<String, String> RESULT_KA = Map.ofEntries(
            Map.entry("NO_ANSWER", "არ უპასუხეს"), Map.entry("TALKED", "ისაუბრეს"), Map.entry("INTERESTED", "დაინტერესდნენ"),
            Map.entry("NOT_INTERESTED", "უარი თქვეს"), Map.entry("CALL_BACK", "მოგვიანებით დარეკვა"), Map.entry("MEETING_SET", "შეხვედრა შეთანხმდა"),
            Map.entry("SAMPLES_REQUESTED", "სემპლი მოითხოვეს"), Map.entry("ORDERED", "შეუკვეთეს"), Map.entry("WRONG_NUMBER", "არასწორი ნომერი"),
            Map.entry("OTHER", "სხვა"));
    private static final Map<String, String> PRIORITY_KA = Map.of("LOW", "დაბალი", "NORMAL", "საშუალო", "HIGH", "მაღალი");

    private final JsonMapper json;
    private final CrmProperties properties;

    public BackupConverter(JsonMapper json, CrmProperties properties) {
        this.json = json;
        this.properties = properties;
    }

    public byte[] toExcel(byte[] upload, boolean ka) throws IOException {
        JsonNode root = json.readTree(jsonOf(upload));
        String format = root.path("format").asString("");
        BackupBuilder.BackupFile backup = null;
        JsonFile crm;
        if (BackupBuilder.FORMAT.equals(format)) {
            backup = json.treeToValue(root, BackupBuilder.BackupFile.class);
            crm = backup.crm();
        } else if ("andaneri-crm".equals(format)) {
            crm = json.treeToValue(root, JsonFile.class);
        } else {
            throw ApiException.badRequest("WRONG_FORMAT");
        }
        List<JsonBusiness> businesses = crm == null || crm.businesses() == null ? List.of() : crm.businesses().stream().filter(Objects::nonNull).toList();
        DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(properties.zoneId());

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Set<String> fields = new LinkedHashSet<>();
            businesses.forEach(b -> { if (b.customFields() != null) fields.addAll(b.customFields().keySet()); });

            List<String> headers = new ArrayList<>(ka
                    ? List.of("პროექტი", "ფურცელი", "სავაჭრო დასახელება", "შპს დასახელება", "ტიპი", "სტატუსი", "პრიორიტეტი", "მისამართი", "ქალაქი",
                            "რაიონი", "ტელეფონი", "ელ.ფოსტა", "ვებ გვერდი", "ს.კ.", "ფილიალები", "ვიზიტის დრო", "პასუხისმგებელი", "კონტაქტები",
                            "ბრენდები", "გემოები", "დაინტერესება", "შეკვეთები", "შენიშვნები", "დაემატა")
                    : List.of("Project", "Sheet", "Name", "Legal name", "Type", "Status", "Priority", "Address", "City", "District", "Phone",
                            "Email", "Website", "ID code", "Branches", "Visit hours", "Assigned to", "Contacts", "Brands", "Flavors",
                            "Interested in", "Orders", "Notes", "Added"));
            headers.addAll(fields);
            List<List<Object>> rows = new ArrayList<>();
            for (JsonBusiness b : businesses) {
                List<Object> row = new ArrayList<>(List.of(
                        text(b.project()), text(b.sheet()), text(b.name()), text(b.legalName()), text(b.type()), code(b.status(), STATUS_KA, ka),
                        code(b.priority(), PRIORITY_KA, ka), text(b.address()), text(b.city()), text(b.district()), text(b.phone()), text(b.email()),
                        text(b.website()), text(b.idCode())));
                row.add(b.branches());
                row.add(text(b.visitHours()));
                row.add(text(b.assignedTo()));
                row.add(join(b.contacts(), c -> String.join(" ", nonBlank(c.name(), c.roleTitle() == null ? null : "(" + c.roleTitle() + ")", c.phone()))));
                row.add(distinct(b.usages(), u -> u.brand()));
                row.add(distinct(b.usages(), u -> u.flavor()));
                row.add(distinct(b.interests(), i -> i.flavor() != null ? i.flavor() : i.product()));
                row.add(b.purchases() == null ? 0 : b.purchases().size());
                row.add(text(b.notes()));
                row.add(format(time, b.createdAt()));
                fields.forEach(f -> row.add(b.customFields() == null ? "" : text(b.customFields().get(f))));
                rows.add(row);
            }
            sheet(workbook, ka ? "ბიზნესები" : "Businesses", headers, rows, 3);

            List<List<Object>> contacts = new ArrayList<>();
            List<List<Object>> activities = new ArrayList<>();
            List<List<Object>> tasks = new ArrayList<>();
            List<List<Object>> orders = new ArrayList<>();
            List<List<Object>> comments = new ArrayList<>();
            for (JsonBusiness b : businesses) {
                each(b.contacts(), c -> contacts.add(List.of(text(b.name()), text(c.name()), text(c.roleTitle()), text(c.phone()), text(c.email()),
                        text(c.preferredChannel()), Boolean.TRUE.equals(c.decisionMaker()) ? (ka ? "კი" : "yes") : "", text(c.notes()))));
                each(b.activities(), a -> activities.add(List.of(text(b.name()), format(time, a.occurredAt()), code(a.type(), TYPE_KA, ka),
                        code(a.result(), RESULT_KA, ka), text(a.user()), text(a.contact()), text(a.notes()),
                        Boolean.TRUE.equals(a.imported()) ? "Excel" : "")));
                each(b.tasks(), t -> tasks.add(List.of(text(b.name()), format(time, t.dueAt()), code(t.type(), TYPE_KA, ka), text(t.title()),
                        code(t.status(), STATUS_KA, ka), text(t.assignedTo()), text(t.location()), text(t.notes()))));
                each(b.purchases(), p -> each(p.items(), item -> orders.add(List.of(text(b.name()), p.purchaseDate() == null ? "" : p.purchaseDate().toString(),
                        text(item.product()), text(item.description()), number(item.quantity()), number(item.unitPrice()),
                        item.quantity() == null || item.unitPrice() == null ? "" : item.quantity().multiply(item.unitPrice()), text(p.user()), text(p.notes())))));
                each(b.comments(), c -> comments.add(List.of(text(b.name()), format(time, c.createdAt()), text(c.author()), text(c.body()))));
            }
            sheet(workbook, ka ? "კონტაქტები" : "Contacts", ka
                    ? List.of("ბიზნესი", "სახელი", "თანამდებობა", "ტელეფონი", "ელ.ფოსტა", "კავშირი", "იღებს გადაწყვეტას", "შენიშვნა")
                    : List.of("Business", "Name", "Role", "Phone", "Email", "Channel", "Decision maker", "Notes"), contacts, 1);
            sheet(workbook, ka ? "ზარები და ვიზიტები" : "Calls and visits", ka
                    ? List.of("ბიზნესი", "როდის", "ტიპი", "შედეგი", "ვინ", "ვისთან", "რა თქვეს", "წყარო")
                    : List.of("Business", "When", "Type", "Result", "By", "With", "Notes", "Source"), activities, 1);
            sheet(workbook, ka ? "ამოცანები" : "Tasks", ka
                    ? List.of("ბიზნესი", "თარიღი", "ტიპი", "რა", "სტატუსი", "პასუხისმგებელი", "ადგილი", "შენიშვნა")
                    : List.of("Business", "Due", "Type", "Title", "Status", "Assigned to", "Location", "Notes"), tasks, 1);
            sheet(workbook, ka ? "შეკვეთები" : "Orders", ka
                    ? List.of("ბიზნესი", "თარიღი", "პროდუქტი", "აღწერა", "რაოდენობა", "ფასი", "ჯამი", "ვინ", "შენიშვნა")
                    : List.of("Business", "Date", "Product", "Description", "Quantity", "Unit price", "Total", "By", "Notes"), orders, 1);
            sheet(workbook, ka ? "კომენტარები" : "Comments", ka
                    ? List.of("ბიზნესი", "როდის", "ავტორი", "კომენტარი") : List.of("Business", "When", "Author", "Comment"), comments, 1);

            if (backup != null) {
                List<List<Object>> projects = new ArrayList<>();
                each(backup.projects(), p -> {
                    if (p.sheets() == null || p.sheets().isEmpty()) {
                        projects.add(List.of(text(p.name()), "", text(p.sourceFile())));
                    }
                    each(p.sheets(), s -> projects.add(List.of(text(p.name()), text(s.name()), text(p.sourceFile()))));
                });
                each(backup.looseSheets(), s -> projects.add(List.of("", text(s.name()), "")));
                sheet(workbook, ka ? "პროექტები" : "Projects", ka ? List.of("პროექტი", "ფურცელი", "ფაილი") : List.of("Project", "Sheet", "File"), projects, 0);

                List<List<Object>> products = new ArrayList<>();
                if (backup.catalog() != null) {
                    each(backup.catalog().products(), p -> products.add(List.of(text(p.brand()), text(p.category()), text(ka ? p.sectionKa() : p.sectionEn()),
                            text(p.nameKa()), text(p.nameEn()), text(p.packSize()), number(p.price()), p.juicePercent() == null ? "" : p.juicePercent(), text(p.status()))));
                }
                sheet(workbook, ka ? "პროდუქცია" : "Products", ka
                        ? List.of("ბრენდი", "კატეგორია", "სექცია", "დასახელება (ქართ.)", "დასახელება (ინგლ.)", "მოცულობა", "ფასი", "წვენი %", "სტატუსი")
                        : List.of("Brand", "Category", "Section", "Name (ka)", "Name (en)", "Size", "Price", "Juice %", "Status"), products, 0);

                List<List<Object>> team = new ArrayList<>();
                each(backup.users(), u -> team.add(List.of(text(u.username()), text(u.fullName()), text(u.phone()), text(u.role()),
                        u.active() ? (ka ? "კი" : "yes") : (ka ? "არა" : "no"))));
                sheet(workbook, ka ? "გუნდი" : "Team", ka ? List.of("მომხმარებელი", "სახელი", "ტელეფონი", "როლი", "აქტიური")
                        : List.of("Username", "Name", "Phone", "Role", "Active"), team, 0);

                List<List<Object>> notes = new ArrayList<>();
                each(backup.notes(), n -> notes.add(List.of(text(n.user()), text(n.business()), text(n.body()), format(time, n.remindAt()),
                        n.done() ? (ka ? "კი" : "yes") : "", format(time, n.createdAt()))));
                sheet(workbook, ka ? "ჩანაწერები" : "Notes", ka ? List.of("ვინ", "ბიზნესი", "ჩანაწერი", "შეხსენება", "შესრულდა", "როდის")
                        : List.of("User", "Business", "Note", "Reminder", "Done", "Created"), notes, 0);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** The JSON inside: a zip's backup.json (or any .json in it), or the upload itself. */
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

    private static void sheet(SXSSFWorkbook workbook, String name, List<String> headers, List<List<Object>> rows, int frozenColumns) {
        Sheet sheet = workbook.createSheet(name);
        CellStyle bold = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        bold.setFont(font);
        Row head = sheet.createRow(0);
        for (int c = 0; c < headers.size(); c++) {
            head.createCell(c).setCellValue(headers.get(c));
            head.getCell(c).setCellStyle(bold);
            sheet.setColumnWidth(c, Math.min(60, Math.max(12, headers.get(c).length() + 6)) * 256);
        }
        sheet.createFreezePane(frozenColumns, 1);
        int r = 1;
        for (List<Object> values : rows) {
            Row row = sheet.createRow(r++);
            for (int c = 0; c < values.size(); c++) {
                Object value = values.get(c);
                if (value instanceof Number number) {
                    row.createCell(c).setCellValue(number.doubleValue());
                } else if (value != null && !value.toString().isBlank()) {
                    String text = value.toString();
                    row.createCell(c).setCellValue(text.length() > 32000 ? text.substring(0, 32000) : text);
                }
            }
        }
    }

    private static <T> void each(List<T> items, java.util.function.Consumer<T> action) {
        if (items != null) {
            items.stream().filter(Objects::nonNull).forEach(action);
        }
    }

    private static <T> String join(List<T> items, java.util.function.Function<T, String> text) {
        return items == null ? "" : String.join("; ", items.stream().filter(Objects::nonNull).map(text).filter(s -> !s.isBlank()).toList());
    }

    private static <T> String distinct(List<T> items, java.util.function.Function<T, String> text) {
        return items == null ? "" : String.join(", ", items.stream().filter(Objects::nonNull).map(text).filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private static String code(String value, Map<String, String> ka, boolean georgian) {
        if (value == null) {
            return "";
        }
        if (georgian) {
            return ka.getOrDefault(value, value);
        }
        String lower = value.replace('_', ' ').toLowerCase();
        return lower.isEmpty() ? "" : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String format(DateTimeFormatter time, Instant instant) {
        return instant == null ? "" : time.format(instant);
    }

    private static Object number(BigDecimal value) {
        return value == null ? "" : value;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String[] nonBlank(String... parts) {
        return java.util.Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).toArray(String[]::new);
    }
}
