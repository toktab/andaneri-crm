package ge.andaneri.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The working day, end to end, against the real API and an H2 database migrated by the same
 * Flyway script as MySQL: sign in, add a bar, log a call with what they use and the next step,
 * see it on the dashboard, sell to them, find it all on the timeline, export and import.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrmFlowTest {

    @Autowired
    MockMvc mvc;

    @Test
    void theWholeSalesCycle() throws Exception {
        String admin = login("admin", "test-admin-password");

        // The price list and the brands are there from the first start.
        String lookups = call(admin, get("/api/lookups"), 200);
        List<String> brandNames = JsonPath.read(lookups, "$.brands[*].name");
        assertThat(brandNames).contains("Andaneri", "Monin", "1883 Maison Routin");
        List<Object> products = JsonPath.read(call(admin, get("/api/products"), 200), "$[*]");
        // Every line of the September 2026 price list, coming-soon ones included.
        assertThat(products).hasSize(64);
        Integer moninId = idOf(lookups, "$.brands[?(@.name == 'Monin')].id");
        Integer vanillaId = idOf(lookups, "$.flavors[?(@.nameEn == 'Vanilla')].id");
        Integer mangoId = idOf(lookups, "$.flavors[?(@.nameEn == 'Mango')].id");
        Integer syrupId = idOf(lookups, "$.categories[?(@.nameEn == 'Syrup')].id");

        // Admin adds two salespeople.
        call(admin, post("/api/admin/users").content("""
                {"username":"toko","fullName":"Toko","role":"SALES","password":"password-123"}"""), 200);
        call(admin, post("/api/admin/users").content("""
                {"username":"nino","fullName":"Nino","role":"SALES","password":"password-123"}"""), 200);
        String toko = login("toko", "password-123");
        String nino = login("nino", "password-123");
        call(toko, get("/api/admin/users"), 403);

        // Quick add from Google Maps: name, address, phone. It is Toko's.
        String created = call(toko, post("/api/businesses").content("""
                {"name":"Test Bar","address":"Chavchavadze 1","phone":"+995 555 12 34 56",
                 "firstContact":{"name":"Giorgi","roleTitle":"bar manager","phone":"599 11 22 33","decisionMaker":true}}"""), 200);
        Integer barId = JsonPath.read(created, "$.id");
        assertThat((String) JsonPath.read(created, "$.status")).isEqualTo("NEW");
        assertThat((String) JsonPath.read(created, "$.assignedTo.fullName")).isEqualTo("Toko");
        List<String> missing = JsonPath.read(created, "$.missing");
        assertThat(missing).contains("USES_SYRUP", "VISIT_HOURS", "NEXT_STEP").doesNotContain("PHONE", "DECISION_MAKER");

        // The same phone written differently is caught as a duplicate, unless forced.
        String duplicate = call(nino, post("/api/businesses").content("""
                {"name":"Another name","phone":"555123456"}"""), 409);
        assertThat((String) JsonPath.read(duplicate, "$.code")).isEqualTo("DUPLICATE");

        // Nino cannot change Toko's business.
        call(nino, post("/api/businesses/" + barId + "/status").content("""
                {"status":"LOST"}"""), 403);

        // One call, recorded once: result, what they use, what they want, new stage, next step.
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
        call(toko, post("/api/businesses/" + barId + "/activities").content("""
                {"type":"CALL","result":"MEETING_SET","notes":"Uses Monin, wants mango",
                 "newStatus":"MEETING",
                 "usages":[{"categoryId":%d,"brandId":%d,"flavorIds":[%d]}],
                 "interests":{"flavorIds":[%d],"status":"SAMPLE_REQUESTED"},
                 "nextTask":{"type":"MEETING","dueAt":"%s","title":"Tasting"}}"""
                .formatted(syrupId, moninId, vanillaId, mangoId, tomorrow)), 200);

        String detail = call(toko, get("/api/businesses/" + barId), 200);
        assertThat((String) JsonPath.read(detail, "$.status")).isEqualTo("MEETING");
        assertThat((String) JsonPath.read(detail, "$.usages[0].brandName")).isEqualTo("Monin");
        assertThat((String) JsonPath.read(detail, "$.lastActivity.result")).isEqualTo("MEETING_SET");
        List<Object> openTasks = JsonPath.read(detail, "$.openTasks");
        assertThat(openTasks).hasSize(1);
        List<String> nowMissing = JsonPath.read(detail, "$.missing");
        assertThat(nowMissing).doesNotContain("USES_SYRUP", "SYRUP_BRAND", "SYRUP_FLAVORS", "NEXT_STEP").contains("SWITCH_OPENNESS");
        // Monin Vanilla -> our Vanilla; asked for Mango -> our Mango.
        List<String> suggested = JsonPath.read(detail, "$.suggestions[*].product.nameEn");
        assertThat(suggested).contains("Vanilla", "Mango");
        assertThat((String) JsonPath.read(detail, "$.suggestions[0].kind")).isEqualTo("REPLACE");

        // The meeting is on tomorrow's calendar and on the dashboard.
        String dashboard = call(toko, get("/api/dashboard"), 200);
        List<Integer> upcoming = JsonPath.read(dashboard, "$.upcoming[*].businessId");
        assertThat(upcoming).contains(barId);
        List<String> alerts = JsonPath.read(dashboard, "$.alerts[*].kind");
        assertThat(alerts).contains("SAMPLES");

        // They buy: a customer now, Andaneri Mango joins what they use, the interest is marked purchased.
        String catalog = call(toko, get("/api/products"), 200);
        Integer ourMango = idOf(catalog, "$[?(@.nameEn == 'Mango' && @.own == true)].id");
        String purchase = call(toko, post("/api/businesses/" + barId + "/purchases").content("""
                {"purchaseDate":"%s","items":[{"productId":%d,"quantity":6,"unitPrice":24}]}"""
                .formatted(LocalDate.now(ZoneId.of("Asia/Tbilisi")), ourMango)), 200);
        assertThat(((Number) JsonPath.read(purchase, "$.total")).doubleValue()).isEqualTo(144.0);
        detail = call(toko, get("/api/businesses/" + barId), 200);
        assertThat((String) JsonPath.read(detail, "$.status")).isEqualTo("CUSTOMER");
        List<String> interestStatus = JsonPath.read(detail, "$.interests[*].status");
        assertThat(interestStatus).containsExactly("PURCHASED");
        List<String> brandsUsed = JsonPath.read(detail, "$.usages[*].brandName");
        assertThat(brandsUsed).contains("Monin", "Andaneri");

        // The supervisor can comment on anyone's business; the timeline has the whole story.
        call(admin, post("/api/businesses/" + barId + "/comments").content("""
                {"body":"Offer them the signature syrups next"}"""), 200);
        String timeline = call(nino, get("/api/businesses/" + barId + "/timeline"), 200);
        List<String> kinds = JsonPath.read(timeline, "$[*].kind");
        assertThat(kinds).contains("CREATED", "STATUS", "ACTIVITY", "PURCHASE", "COMMENT");

        // Filters: uses Monin; interested in Mango; customers.
        // Other tests share this database, so each filter is narrowed to this business.
        assertThat((Integer) JsonPath.read(call(nino, get("/api/businesses?q=Test Bar&brandId=" + moninId), 200), "$.total")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(call(nino, get("/api/businesses?q=Test Bar&interestFlavorId=" + vanillaId), 200), "$.total")).isEqualTo(0);
        assertThat((Integer) JsonPath.read(call(nino, get("/api/businesses?customer=true&q=test"), 200), "$.total")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(call(nino, get("/api/businesses?status=LOST"), 200), "$.total")).isEqualTo(0);

        // Reports count the call and the sale.
        String report = call(admin, get("/api/reports"), 200);
        assertThat((Integer) JsonPath.read(report, "$.calls")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) JsonPath.read(report, "$.newCustomers")).isGreaterThanOrEqualTo(1);

        // Export gives a real xlsx (a zip file).
        MvcResult export = mvc.perform(get("/api/export/businesses").header("Authorization", "Bearer " + toko)).andReturn();
        assertThat(export.getResponse().getStatus()).isEqualTo(200);
        byte[] xlsx = export.getResponse().getContentAsByteArray();
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        assertThat(xlsx[1]).isEqualTo((byte) 'K');

        // Quick notes stay private until saved onto a business.
        String note = call(toko, post("/api/notes").content("""
                {"body":"ask if the bartender tasted it","businessId":%d}""".formatted(barId)), 200);
        Integer noteId = JsonPath.read(note, "$.id");
        List<Object> ninoNotes = JsonPath.read(call(nino, get("/api/notes"), 200), "$[*]");
        assertThat(ninoNotes).isEmpty();
        call(toko, post("/api/notes/" + noteId + "/to-comment"), 200);
        timeline = call(toko, get("/api/businesses/" + barId + "/timeline"), 200);
        List<String> notes = JsonPath.read(timeline, "$[?(@.kind == 'COMMENT')].notes");
        assertThat(notes).contains("ask if the bartender tasted it");
    }

    @Test
    void importsTheSalesReportForm() throws Exception {
        String admin = login("admin", "test-admin-password");
        String headers = """
                ["სავაჭრო დასახელება","მისამართი","ტელეფონი","ს.კ.","დირექტორი/მენეჯერი სახელი, ტელ. მეილ",
                 "მოიხმარენ თუ არა სიროფს კი/არა; თუ კი, რომელი ბრენდის","რა სახეობებს და თვეში რამდენს",
                 "I დაკავშირების თარიღი","გახდა თუ არა კლიენტი და როდის","თავისუფალი კომენტარი","შემდეგი ნაბიჯი"]""";
        String rows = """
                [["Import Cafe One","Paliashvili, 24","(596) 900 010","402023387","ნატო (მენეჯერი) 577 20 28 40",
                  "კი, მონინი","გრენადინი, ვანილი","24,08,2026 ჩავუგდეთ პრაისები","არა","2/9-ში დავრეკე და არაიღეს","დავურეკოთ 20/9"],
                 ["Import Bar Two","Lado Asatiani, 28","599168365","","","არა","","","","",""],
                 ["","no name","","","","","","","","",""]]""";
        String mapping = """
                {"0":"NAME","1":"ADDRESS","2":"PHONE","3":"ID_CODE","4":"CONTACTS","5":"SYRUP_USAGE","6":"FLAVORS",
                 "7":"HISTORY","8":"CUSTOMER","9":"COMMENT","10":"NEXT_STEP"}""";
        String body = """
                {"headers":%s,"rows":%s,"mapping":%s,"source":"test.xlsx",
                 "options":{"skipDuplicates":true,"createFollowUps":true,"city":"თბილისი"}}""".formatted(headers, rows, mapping);

        String preview = call(admin, post("/api/import/preview").content(body), 200);
        assertThat((Integer) JsonPath.read(preview, "$.ready")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(preview, "$.invalid")).isEqualTo(1);
        List<String> brands = JsonPath.read(preview, "$.rows[0].brands");
        assertThat(brands).containsExactly("Monin");

        String result = call(admin, post("/api/import/commit").content(body), 200);
        assertThat((Integer) JsonPath.read(result, "$.created")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(result, "$.tasks")).isEqualTo(1);

        String search = call(admin, get("/api/businesses?q=Import Cafe One"), 200);
        Integer id = JsonPath.read(search, "$.items[0].id");
        String detail = call(admin, get("/api/businesses/" + id), 200);
        assertThat((String) JsonPath.read(detail, "$.idCode")).isEqualTo("402023387");
        assertThat((String) JsonPath.read(detail, "$.city")).isEqualTo("თბილისი");
        assertThat((String) JsonPath.read(detail, "$.contacts[0].name")).isEqualTo("ნატო");
        List<String> flavors = JsonPath.read(detail, "$.usages[*].flavor.nameEn");
        assertThat(flavors).containsExactlyInAnyOrder("Grenadine", "Vanilla");
        String timeline = call(admin, get("/api/businesses/" + id + "/timeline"), 200);
        List<Boolean> imported = JsonPath.read(timeline, "$[?(@.kind == 'ACTIVITY')].imported");
        assertThat(imported).isNotEmpty().allMatch(Boolean.TRUE::equals);
        List<String> texts = JsonPath.read(timeline, "$[?(@.kind == 'ACTIVITY')].notes");
        assertThat(texts).anyMatch(t -> t.contains("2/9-ში დავრეკე და არაიღეს"));

        // Importing the same sheet again creates nothing new.
        String again = call(admin, post("/api/import/preview").content(body), 200);
        assertThat((Integer) JsonPath.read(again, "$.duplicates")).isEqualTo(2);
    }

    @Test
    void jsonRoundTripAndTheDetailedReport() throws Exception {
        String admin = login("admin", "test-admin-password");
        String catalog = call(admin, get("/api/products"), 200);
        Integer ourVanilla = idOf(catalog, "$[?(@.nameEn == 'Vanilla' && @.own == true)].id");

        String created = call(admin, post("/api/businesses").content("""
                {"name":"Json Lounge","phone":"+995 551 00 00 01","district":"ვაკე",
                 "firstContact":{"name":"Mariam","roleTitle":"owner","decisionMaker":true}}"""), 200);
        Integer id = JsonPath.read(created, "$.id");
        call(admin, post("/api/businesses/" + id + "/activities").content("""
                {"type":"CALL","result":"NO_ANSWER"}"""), 200);
        call(admin, post("/api/businesses/" + id + "/activities").content("""
                {"type":"MEETING","result":"INTERESTED","notes":"liked vanilla"}"""), 200);
        call(admin, post("/api/businesses/" + id + "/purchases").content("""
                {"purchaseDate":"%s","items":[{"productId":%d,"quantity":3,"unitPrice":18}]}"""
                .formatted(LocalDate.now(ZoneId.of("Asia/Tbilisi")), ourVanilla)), 200);

        // The detailed numbers: the no-answer call, the meeting that said yes, bottles, flavors sold.
        String report = call(admin, get("/api/reports"), 200);
        assertThat((Integer) JsonPath.read(report, "$.funnel.callsNoAnswer")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) JsonPath.read(report, "$.funnel.meetingsSaidYes")).isGreaterThanOrEqualTo(1);
        assertThat(((Number) JsonPath.read(report, "$.bottlesSold")).doubleValue()).isGreaterThanOrEqualTo(3.0);
        List<String> sold = JsonPath.read(report, "$.flavorsSold[*].nameEn");
        assertThat(sold).contains("Vanilla");

        MvcResult xlsx = mvc.perform(get("/api/reports/export").header("Authorization", "Bearer " + admin)).andReturn();
        assertThat(xlsx.getResponse().getContentAsByteArray()[0]).isEqualTo((byte) 'P');
        String onlyFunnel = mvc.perform(get("/api/reports/export?format=json&sections=funnel").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(onlyFunnel).contains("\"funnel\"").doesNotContain("\"summary\"");

        // Export as JSON with the whole history...
        String file = mvc.perform(get("/api/export/businesses?format=json&q=Json Lounge").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(file, "$.format")).isEqualTo("andaneri-crm");
        assertThat((String) JsonPath.read(file, "$.businesses[0].contacts[0].name")).isEqualTo("Mariam");
        List<Object> history = JsonPath.read(file, "$.businesses[0].activities");
        assertThat(history).hasSize(2);

        // ...which, imported back as-is, is recognised as already there.
        String dryRun = mvc.perform(multipart("/api/import/json").file(jsonFile(file)).param("dryRun", "true")
                        .header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((Integer) JsonPath.read(dryRun, "$.skippedDuplicates")).isEqualTo(1);

        // As a different place it is created with its history, order and all.
        String renamed = file.replace("Json Lounge", "Json Lounge Two").replace("+995 551 00 00 01", "+995 551 00 00 02");
        String result = mvc.perform(multipart("/api/import/json").file(jsonFile(renamed)).param("dryRun", "false")
                        .header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((Integer) JsonPath.read(result, "$.created")).isEqualTo(1);
        String found = call(admin, get("/api/businesses?q=Json Lounge Two"), 200);
        assertThat((Integer) JsonPath.read(found, "$.items[0].purchaseCount")).isEqualTo(1);
        Integer copyId = JsonPath.read(found, "$.items[0].id");
        List<String> kinds = JsonPath.read(call(admin, get("/api/businesses/" + copyId + "/timeline"), 200), "$[*].kind");
        assertThat(kinds).contains("ACTIVITY", "PURCHASE");
    }

    private static MockMultipartFile jsonFile(String content) {
        return new MockMultipartFile("file", "backup.json", "application/json", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void wrongPasswordAndNoTokenAreRefused() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
        mvc.perform(get("/api/businesses"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.token");
    }

    private String call(String token, MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body;
    }

    private static Integer idOf(String json, String path) {
        List<Integer> ids = JsonPath.read(json, path);
        assertThat(ids).as(path).isNotEmpty();
        return ids.get(0);
    }
}
