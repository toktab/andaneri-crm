package ge.andaneri.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import ge.andaneri.crm.domain.ActivityRepository;
import ge.andaneri.crm.domain.AuditEntryRepository;
import ge.andaneri.crm.domain.BusinessFieldValueRepository;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.BusinessSheetRepository;
import ge.andaneri.crm.domain.CategoryUsageRepository;
import ge.andaneri.crm.domain.CommentRepository;
import ge.andaneri.crm.domain.ContactRepository;
import ge.andaneri.crm.domain.CustomFieldRepository;
import ge.andaneri.crm.domain.InterestRepository;
import ge.andaneri.crm.domain.ProductUsageRepository;
import ge.andaneri.crm.domain.PurchaseRepository;
import ge.andaneri.crm.domain.QuickNoteRepository;
import ge.andaneri.crm.domain.StatusChangeRepository;
import ge.andaneri.crm.domain.TaskRepository;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.domain.WorkbookRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
 * The promise a backup makes: take the zip to a bare install, restore it, and the CRM is what it was.
 * A day's worth of real work is built up, backed up, every table emptied the way a new server is empty,
 * and the file put back - then the whole thing is read again and compared, down to the cancelled plan,
 * the flavors a delivery was for, the note under one entry and how the business moved through the stages.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackupRestoreRoundTripTest {

    @Autowired MockMvc mvc;
    @Autowired TaskRepository tasks;
    @Autowired ActivityRepository activities;
    @Autowired CommentRepository comments;
    @Autowired PurchaseRepository purchases;
    @Autowired InterestRepository interests;
    @Autowired ProductUsageRepository usages;
    @Autowired CategoryUsageRepository categoryUsages;
    @Autowired StatusChangeRepository statusChanges;
    @Autowired BusinessFieldValueRepository fieldValues;
    @Autowired ContactRepository contacts;
    @Autowired QuickNoteRepository notes;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessSheetRepository sheets;
    @Autowired WorkbookRepository workbooks;
    @Autowired CustomFieldRepository customFields;
    @Autowired AuditEntryRepository auditEntries;
    @Autowired UserRepository users;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** The database is shared with the other tests: leave it as bare as this one found it. */
    @AfterEach
    void clearUp() {
        wipe();
    }

    @Test
    void aBackupPutsTheWholeCrmBackOnABareInstall() throws Exception {
        String admin = login("admin", "test-admin-password");
        String lookups = call(admin, get("/api/lookups"), 200);
        Integer mangoId = idOf(lookups, "$.flavors[?(@.nameEn == 'Mango')].id");
        Integer moninId = idOf(lookups, "$.brands[?(@.name == 'Monin')].id");
        Integer syrupId = idOf(lookups, "$.categories[?(@.nameEn == 'Syrup')].id");

        // --- a day of work -------------------------------------------------------------------
        call(admin, post("/api/admin/users").content("""
                {"username":"nino","fullName":"Nino","role":"SALES","password":"password-123"}"""), 200);

        Integer barId = JsonPath.read(call(admin, post("/api/businesses").content("""
                {"name":"Round Trip Bar","phone":"555 60 70 80","address":"Kote Abkhazi 7","visitHours":"17:00-19:00",
                 "firstContact":{"name":"Giorgi","roleTitle":"bar manager","phone":"599 60 70 80","decisionMaker":true}}"""), 200), "$.id");

        String visit = call(admin, post("/api/businesses/" + barId + "/activities").content("""
                {"type":"VISIT","result":"SPONTANEOUS_VISIT","results":["SPONTANEOUS_VISIT","SAMPLES_LEFT"],
                 "resultNote":"ბარმენმა თქვა, ხვალ მოდიო","notes":"შევიარეთ გზად","newStatus":"TESTING",
                 "usages":[{"categoryId":%d,"brandId":%d,"flavorIds":[%d]}],
                 "interests":{"flavorIds":[%d],"status":"TESTING","feedback":"LIKED"},
                 "nextTask":{"type":"DELIVERY","dueAt":"%s","title":"მიტანა","flavorIds":[%d],"remindMinutes":60}}"""
                .formatted(syrupId, moninId, mangoId, mangoId,
                        Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS), mangoId)), 200);
        Integer visitId = JsonPath.read(visit, "$.id");
        call(admin, post("/api/businesses/" + barId + "/comments").content("""
                {"body":"რაც ჩანაწერზეა მიბმული","activityId":%d}""".formatted(visitId)), 200);
        call(admin, post("/api/businesses/" + barId + "/comments").content("""
                {"body":"ცალკე კომენტარი ბიზნესზე"}"""), 200);

        // One plan ticked off and one cancelled: both have to come back saying so.
        Integer doneTask = JsonPath.read(call(admin, post("/api/tasks").content("""
                {"businessId":%d,"type":"CALL","title":"დარეკვა","dueAt":"%s"}"""
                .formatted(barId, Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))), 200), "$.id");
        call(admin, post("/api/tasks/" + doneTask + "/complete").content("{}"), 200);
        Integer cancelledTask = JsonPath.read(call(admin, post("/api/tasks").content("""
                {"businessId":%d,"type":"MEETING","title":"შეხვედრა","dueAt":"%s"}"""
                .formatted(barId, Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))), 200), "$.id");
        call(admin, post("/api/tasks/" + cancelledTask + "/cancel"), 200);

        call(admin, post("/api/tasks").content("""
                {"businessId":%d,"type":"CALL","title":"შეხსენებით","dueAt":"%s","remindMinutes":120}"""
                .formatted(barId, Instant.now().plus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))), 200);

        call(admin, post("/api/businesses/" + barId + "/purchases").content("""
                {"purchaseDate":"2026-09-01","items":[{"description":"ვანილი","quantity":6,"unitPrice":18}]}"""), 200);
        call(admin, post("/api/notes").content("""
                {"body":"პირადი შეხსენება","businessId":%d}""".formatted(barId)), 200);

        String before = call(admin, get("/api/businesses/" + barId), 200);
        String beforeTimeline = call(admin, get("/api/businesses/" + barId + "/timeline"), 200);
        int beforeActivities = ((List<?>) JsonPath.read(beforeTimeline, "$[?(@.kind == 'ACTIVITY')]")).size();

        // --- the backup ----------------------------------------------------------------------
        Integer backupId = JsonPath.read(call(admin, post("/api/admin/backups"), 200), "$.id");
        byte[] zip = mvc.perform(get("/api/admin/backups/" + backupId + "/download").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(zip).isNotEmpty();

        // --- a bare install ------------------------------------------------------------------
        wipe();
        assertThat(businesses.findAll()).isEmpty();
        assertThat(users.findByUsernameIgnoreCase("nino")).isEmpty();

        // --- put it back ---------------------------------------------------------------------
        MvcResult restored = mvc.perform(multipart("/api/admin/backups/restore")
                        .file(new MockMultipartFile("file", "backup.zip", "application/zip", zip))
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        String result = restored.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(restored.getResponse().getStatus()).as(result).isEqualTo(200);
        assertThat((Integer) JsonPath.read(result, "$.businesses")).isEqualTo(1);
        List<String> waiting = JsonPath.read(result, "$.usersNeedingPassword");
        assertThat(waiting).contains("nino");
        // A restored account cannot be signed into until an admin gives it a password.
        assertThat(users.findByUsernameIgnoreCase("nino")).isPresent().get()
                .satisfies(u -> assertThat(u.isActive()).isFalse());

        // --- and it is the same CRM ----------------------------------------------------------
        Integer restoredId = JsonPath.read(call(admin, get("/api/businesses?q=Round Trip Bar"), 200), "$.items[0].id");
        String after = call(admin, get("/api/businesses/" + restoredId), 200);
        assertThat((String) JsonPath.read(after, "$.status")).isEqualTo(JsonPath.read(before, "$.status"));
        assertThat((String) JsonPath.read(after, "$.visitHours")).isEqualTo("17:00-19:00");
        assertThat((String) JsonPath.read(after, "$.contacts[0].name")).isEqualTo("Giorgi");
        assertThat((Boolean) JsonPath.read(after, "$.contacts[0].decisionMaker")).isTrue();
        List<String> usedFlavors = JsonPath.read(after, "$.usages[*].flavor.nameEn");
        assertThat(usedFlavors).containsExactly("Mango");
        List<String> wanted = JsonPath.read(after, "$.interests[*].flavor.nameEn");
        assertThat(wanted).containsExactly("Mango");
        assertThat((Integer) JsonPath.read(after, "$.purchases.count")).isEqualTo(1);

        // The delivery still knows what it is for, and when to remind.
        List<Object> delivery = JsonPath.read(after, "$.openTasks[?(@.type == 'DELIVERY')]");
        assertThat(delivery).hasSize(1);
        List<Integer> deliverFlavors = JsonPath.read(after, "$.openTasks[?(@.type == 'DELIVERY')].flavorIds[*]");
        assertThat(deliverFlavors).hasSize(1);
        List<Integer> remind = JsonPath.read(after, "$.openTasks[?(@.title == 'შეხსენებით')].remindMinutes");
        assertThat(remind).containsExactly(120);

        // The entry says everything it said before, with its comment under it.
        String timeline = call(admin, get("/api/businesses/" + restoredId + "/timeline"), 200);
        assertThat(((List<?>) JsonPath.read(timeline, "$[?(@.kind == 'ACTIVITY')]")).size()).isEqualTo(beforeActivities);
        List<String> results = JsonPath.read(timeline, "$[?(@.kind == 'ACTIVITY')].results[*]");
        assertThat(results).contains("SPONTANEOUS_VISIT", "SAMPLES_LEFT");
        List<String> resultNotes = JsonPath.read(timeline, "$[?(@.kind == 'ACTIVITY')].resultNote");
        assertThat(resultNotes).contains("ბარმენმა თქვა, ხვალ მოდიო");
        List<String> nested = JsonPath.read(timeline, "$[?(@.kind == 'ACTIVITY')].comments[*].body");
        assertThat(nested).contains("რაც ჩანაწერზეა მიბმული");
        List<String> looseComments = JsonPath.read(timeline, "$[?(@.kind == 'COMMENT')].notes");
        assertThat(looseComments).contains("ცალკე კომენტარი ბიზნესზე");

        // The plan that was ticked off and the one that was called off are both still on the record.
        List<String> kinds = JsonPath.read(timeline, "$[*].kind");
        assertThat(kinds).contains("TASK_DONE", "TASK_CANCELLED", "PURCHASE", "STATUS");
        List<String> stages = JsonPath.read(timeline, "$[?(@.kind == 'STATUS')].toStatus");
        assertThat(stages).contains("TESTING");

        // The private note came back to its owner.
        assertThat(notes.findAll()).hasSize(1);
        assertThat(notes.findAll().get(0).getBody()).isEqualTo("პირადი შეხსენება");
    }

    /** What a new server looks like: the tables empty, only the account doing the restore left. */
    private void wipe() {
        jdbc.update("delete from backup_events");
        jdbc.update("delete from backups");
        auditEntries.deleteAll();
        notes.deleteAll();
        tasks.deleteAll();
        comments.deleteAll();
        activities.deleteAll();
        purchases.deleteAll();
        interests.deleteAll();
        usages.deleteAll();
        categoryUsages.deleteAll();
        statusChanges.deleteAll();
        fieldValues.deleteAll();
        contacts.deleteAll();
        businesses.deleteAll();
        sheets.deleteAll();
        workbooks.deleteAll();
        customFields.deleteAll();
        users.findAll().stream().filter(u -> !"admin".equalsIgnoreCase(u.getUsername())).forEach(users::delete);
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
