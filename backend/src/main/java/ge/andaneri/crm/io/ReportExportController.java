package ge.andaneri.crm.io;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.BusinessTypeRepository;
import ge.andaneri.crm.service.BusinessService;
import ge.andaneri.crm.service.ReportService;
import ge.andaneri.crm.service.ReportService.FlavorRow;
import ge.andaneri.crm.service.ReportService.Funnel;
import ge.andaneri.crm.service.ReportService.Report;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * The report as a file: an Excel workbook with one sheet per chosen section, or the same numbers
 * as JSON. Sections are picked on the report page; none picked means all of them.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportExportController {

    static final List<String> SECTIONS = List.of("summary", "funnel", "users", "products", "flavorsSold", "flavorsWanted",
            "flavorsLiked", "flavorsDisliked", "marketBrands", "marketFlavors", "types", "districts", "pipeline");

    private final ReportService reports;
    private final BusinessService businesses;
    private final BusinessTypeRepository types;
    private final CurrentUser currentUser;
    private final JsonMapper json;

    public ReportExportController(ReportService reports, BusinessService businesses, BusinessTypeRepository types,
            CurrentUser currentUser, JsonMapper json) {
        this.reports = reports;
        this.businesses = businesses;
        this.types = types;
        this.currentUser = currentUser;
        this.json = json;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) List<String> sections,
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(defaultValue = "ka") String lang) throws IOException {
        currentUser.require();
        LocalDate end = to == null ? businesses.today() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        Report report = reports.build(start.isAfter(end) ? end : start, start.isAfter(end) ? start : end, userId);
        Set<String> wanted = sections == null || sections.isEmpty() ? Set.copyOf(SECTIONS) : Set.copyOf(sections);
        String base = "report-" + report.from() + "-" + report.to();

        if ("json".equals(format)) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("from", report.from());
            out.put("to", report.to());
            if (wanted.contains("summary")) {
                out.put("summary", summary(report, true));
            }
            putIf(out, wanted, "funnel", report.funnel());
            putIf(out, wanted, "users", report.byUser());
            putIf(out, wanted, "products", report.byProduct());
            putIf(out, wanted, "flavorsSold", report.flavorsSold());
            putIf(out, wanted, "flavorsWanted", report.flavorsWanted());
            putIf(out, wanted, "flavorsLiked", report.flavorsLiked());
            putIf(out, wanted, "flavorsDisliked", report.flavorsDisliked());
            putIf(out, wanted, "marketBrands", report.marketBrands());
            putIf(out, wanted, "marketFlavors", report.marketFlavors());
            putIf(out, wanted, "types", report.byType());
            putIf(out, wanted, "districts", report.byDistrict());
            putIf(out, wanted, "pipeline", report.pipeline());
            return file(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(out), base + ".json", MediaType.APPLICATION_JSON);
        }

        boolean ka = !"en".equals(lang);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);
            if (wanted.contains("summary")) {
                List<Object[]> rows = new ArrayList<>();
                summary(report, ka).forEach((k, v) -> rows.add(new Object[] {k, v}));
                sheet(workbook, bold, ka ? "შეჯამება" : "Summary", ka ? new String[] {"მაჩვენებელი", "მნიშნელობა"} : new String[] {"Metric", "Value"}, rows);
            }
            if (wanted.contains("funnel")) {
                List<Object[]> rows = new ArrayList<>();
                funnel(report.funnel(), ka).forEach((k, v) -> rows.add(new Object[] {k, v}));
                sheet(workbook, bold, ka ? "ზარები და შეხვედრები" : "Calls and meetings", ka ? new String[] {"მაჩვენებელი", "რაოდენობა"} : new String[] {"Metric", "Count"}, rows);
            }
            if (wanted.contains("users")) {
                sheet(workbook, bold, ka ? "გუნდი" : "Team",
                        ka ? new String[] {"თანამშრომელი", "ზარები", "ნაპასუხები", "ვიზიტები", "შეხვედრები", "ახალი ლიდები", "ახალი კლიენტები", "შეკვეთები", "ბოთლები", "გაყიდვები ₾", "შესრულებული ამოცანები"}
                                : new String[] {"Person", "Calls", "Reached", "Visits", "Meetings", "New leads", "New clients", "Orders", "Bottles", "Sales GEL", "Tasks done"},
                        report.byUser().stream().map(u -> new Object[] {u.name(), u.calls(), u.callsReached(), u.visits(), u.meetings(),
                                u.newLeads(), u.newCustomers(), u.purchases(), u.bottles(), u.sales(), u.tasksDone()}).toList());
            }
            if (wanted.contains("products")) {
                sheet(workbook, bold, ka ? "პროდუქტები" : "Products",
                        ka ? new String[] {"პროდუქტი", "ბოთლები", "ჯამი ₾"} : new String[] {"Product", "Bottles", "Total GEL"},
                        report.byProduct().stream().map(p -> new Object[] {ka ? p.nameKa() : p.nameEn(), p.quantity(), p.total()}).toList());
            }
            flavorSheet(workbook, bold, wanted, "flavorsSold", ka ? "ყველაზე გაყიდვადი გემოები" : "Flavors sold", ka, report.flavorsSold(), true);
            flavorSheet(workbook, bold, wanted, "flavorsWanted", ka ? "ყველაზე მოთხოვნადი გემოები" : "Flavors wanted", ka, report.flavorsWanted(), false);
            flavorSheet(workbook, bold, wanted, "flavorsLiked", ka ? "ყველაზე მოწონებული" : "Flavors liked", ka, report.flavorsLiked(), false);
            flavorSheet(workbook, bold, wanted, "flavorsDisliked", ka ? "არ მოეწონა" : "Flavors disliked", ka, report.flavorsDisliked(), false);
            if (wanted.contains("marketBrands")) {
                sheet(workbook, bold, ka ? "ბრენდები ბაზარზე" : "Brands in the market",
                        ka ? new String[] {"ბრენდი", "ჩვენი", "ბიზნესები"} : new String[] {"Brand", "Ours", "Businesses"},
                        report.marketBrands().stream().map(b -> new Object[] {b.name(), b.own() ? (ka ? "კი" : "yes") : "", b.businesses()}).toList());
            }
            flavorSheet(workbook, bold, wanted, "marketFlavors", ka ? "კონკურენტების გემოები" : "Competitor flavors", ka, report.marketFlavors(), false);
            if (wanted.contains("types")) {
                sheet(workbook, bold, ka ? "ტიპების მიხედვით" : "By type",
                        ka ? new String[] {"ტიპი", "შეკვეთები", "ჯამი ₾"} : new String[] {"Type", "Orders", "Total GEL"},
                        report.byType().stream().map(g -> new Object[] {typeName(g.key(), ka), g.purchases(), g.total()}).toList());
            }
            if (wanted.contains("districts")) {
                sheet(workbook, bold, ka ? "რაიონების მიხედვით" : "By district",
                        ka ? new String[] {"რაიონი", "შეკვეთები", "ჯამი ₾"} : new String[] {"District", "Orders", "Total GEL"},
                        report.byDistrict().stream().map(g -> new Object[] {g.key(), g.purchases(), g.total()}).toList());
            }
            if (wanted.contains("pipeline")) {
                sheet(workbook, bold, ka ? "სტატუსები" : "Pipeline", ka ? new String[] {"სტატუსი", "ბიზნესები"} : new String[] {"Status", "Businesses"},
                        report.pipeline().entrySet().stream().map(e -> new Object[] {statusName(e.getKey(), ka), e.getValue()}).toList());
            }
            if (workbook.getNumberOfSheets() == 0) {
                workbook.createSheet("-");
            }
            workbook.write(out);
            return file(out.toByteArray(), base + ".xlsx",
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        }
    }

    private static Map<String, Object> summary(Report r, boolean ka) {
        Map<String, Object> rows = new LinkedHashMap<>();
        rows.put(ka ? "პერიოდი" : "Period", r.from() + " - " + r.to());
        rows.put(ka ? "ახალი ლიდები" : "New leads", r.newLeads());
        rows.put(ka ? "ზარები" : "Calls", r.calls());
        rows.put(ka ? "ვიზიტები" : "Visits", r.visits());
        rows.put(ka ? "შეხვედრები" : "Meetings", r.meetings());
        rows.put(ka ? "გაგზავნილი სემპლები" : "Samples sent", r.samples());
        rows.put(ka ? "ახალი კლიენტები" : "New clients", r.newCustomers());
        rows.put(ka ? "უარი / დაკარგული" : "Said no / lost", r.lost());
        rows.put(ka ? "შეკვეთები" : "Orders", r.purchases());
        rows.put(ka ? "გაყიდული ბოთლები" : "Bottles sold", r.bottlesSold());
        rows.put(ka ? "გაყიდვები ₾" : "Sales GEL", r.salesTotal());
        rows.put(ka ? "კონვერსია %" : "Conversion %", r.conversionPercent());
        rows.put(ka ? "შესრულებული ამოცანები" : "Tasks done", r.tasksDone());
        rows.put(ka ? "ვადაგადაცილებული ახლა" : "Overdue now", r.overdueNow());
        return rows;
    }

    private static Map<String, Object> funnel(Funnel f, boolean ka) {
        Map<String, Object> rows = new LinkedHashMap<>();
        rows.put(ka ? "ზარები სულ" : "Calls", f.calls());
        rows.put(ka ? "არ უპასუხეს" : "Not answered", f.callsNoAnswer());
        rows.put(ka ? "უპასუხეს" : "Answered", f.callsAnswered());
        rows.put(ka ? "ზარი - თქვეს კი" : "Calls - said yes", f.callsSaidYes());
        rows.put(ka ? "ზარი - თქვეს არა" : "Calls - said no", f.callsSaidNo());
        rows.put(ka ? "ზარი - ისაუბრეს, პასუხის გარეშე" : "Calls - talked, no decision", f.callsTalked());
        rows.put(ka ? "შეხვედრები" : "Meetings", f.meetings());
        rows.put(ka ? "შეხვედრა - თქვეს კი" : "Meetings - said yes", f.meetingsSaidYes());
        rows.put(ka ? "შეხვედრა - თქვეს არა" : "Meetings - said no", f.meetingsSaidNo());
        rows.put(ka ? "ვიზიტები" : "Visits", f.visits());
        rows.put(ka ? "ვიზიტი - თქვეს კი" : "Visits - said yes", f.visitsSaidYes());
        rows.put(ka ? "ვიზიტი - თქვეს არა" : "Visits - said no", f.visitsSaidNo());
        rows.put(ka ? "გაგზავნილი სემპლები" : "Samples sent", f.samplesSent());
        rows.put(ka ? "ბიზნესები, ვისაც დავრეკე" : "Businesses called", f.businessesCalled());
        rows.put(ka ? "ბიზნესები, ვინც უპასუხა" : "Businesses reached", f.businessesReached());
        rows.put(ka ? "ბიზნესები, ვისაც შევხვდი" : "Businesses met", f.businessesMet());
        rows.put(ka ? "გახდნენ კლიენტები" : "Became clients", f.becameClients());
        rows.put(ka ? "გაყიდული ბოთლები" : "Bottles sold", f.bottlesSold());
        return rows;
    }

    private void flavorSheet(XSSFWorkbook workbook, CellStyle bold, Set<String> wanted, String key, String title,
            boolean ka, List<FlavorRow> rows, boolean withQuantity) {
        if (!wanted.contains(key)) {
            return;
        }
        String[] headers = withQuantity
                ? (ka ? new String[] {"გემო", "ბოთლები", "ბიზნესები"} : new String[] {"Flavor", "Bottles", "Businesses"})
                : (ka ? new String[] {"გემო", "ბიზნესები"} : new String[] {"Flavor", "Businesses"});
        sheet(workbook, bold, title, headers, rows.stream()
                .map(f -> withQuantity ? new Object[] {ka ? f.nameKa() : f.nameEn(), f.quantity(), f.count()}
                        : new Object[] {ka ? f.nameKa() : f.nameEn(), f.count()})
                .toList());
    }

    private static void sheet(XSSFWorkbook workbook, CellStyle bold, String title, String[] headers, List<Object[]> rows) {
        // Excel sheet names: at most 31 characters, none of []:*?/\
        Sheet sheet = workbook.createSheet(title.replaceAll("[\\[\\]:*?/\\\\]", " ").substring(0, Math.min(31, title.length())));
        Row head = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            head.createCell(c).setCellValue(headers[c]);
            head.getCell(c).setCellStyle(bold);
        }
        int r = 1;
        for (Object[] values : rows) {
            Row row = sheet.createRow(r++);
            for (int c = 0; c < values.length; c++) {
                Object value = values[c];
                if (value instanceof BigDecimal decimal) {
                    row.createCell(c).setCellValue(decimal.doubleValue());
                } else if (value instanceof Number number) {
                    row.createCell(c).setCellValue(number.doubleValue());
                } else if (value != null) {
                    row.createCell(c).setCellValue(value.toString());
                }
            }
        }
        for (int c = 0; c < headers.length; c++) {
            sheet.autoSizeColumn(c);
        }
    }

    private String typeName(String id, boolean ka) {
        if (id == null || id.isBlank()) {
            return ka ? "ტიპის გარეშე" : "No type";
        }
        return types.findById(Long.valueOf(id)).map(t -> ka ? t.getNameKa() : t.getNameEn()).orElse(id);
    }

    private static String statusName(BusinessStatus status, boolean ka) {
        if (!ka) {
            return status.name();
        }
        return switch (status) {
            case NEW -> "ახალი";
            case CONTACTED -> "დაკავშირებული";
            case INTERESTED -> "დაინტერესებული";
            case MEETING -> "შეხვედრა";
            case TESTING -> "ტესტირება";
            case NEGOTIATION -> "მოლაპარაკება";
            case CUSTOMER -> "კლიენტი";
            case REPEAT_CUSTOMER -> "მუდმივი კლიენტი";
            case FOLLOW_UP_LATER -> "მოგვიანებით";
            case NOT_INTERESTED -> "არ დაინტერესდა";
            case LOST -> "დაკარგული";
        };
    }

    private static void putIf(Map<String, Object> out, Set<String> wanted, String key, Object value) {
        if (wanted.contains(key)) {
            out.put(key, value);
        }
    }

    static ResponseEntity<byte[]> file(byte[] bytes, String fileName, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''"
                        + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"))
                .contentType(type)
                .body(bytes);
    }
}
