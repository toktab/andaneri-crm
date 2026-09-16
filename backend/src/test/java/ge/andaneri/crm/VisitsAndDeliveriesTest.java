package ge.andaneri.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A visit as it really happens: several things at once, in the team's own words, corrected afterwards
 * when it turns out differently, and a delivery that says which flavors to take along.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VisitsAndDeliveriesTest {

    @Autowired
    MockMvc mvc;

    @Test
    void aVisitCanSayEverythingThatHappened() throws Exception {
        String admin = login("admin", "test-admin-password");
        String lookups = call(admin, get("/api/lookups"), 200);
        Integer mangoId = idOf(lookups, "$.flavors[?(@.nameEn == 'Mango')].id");
        Integer vanillaId = idOf(lookups, "$.flavors[?(@.nameEn == 'Vanilla')].id");

        Integer barId = JsonPath.read(call(admin, post("/api/businesses").content("""
                {"name":"Drop-in Bar","phone":"555 77 88 99"}"""), 200), "$.id");

        // We happened to be nearby, left samples, and they asked about other flavors too.
        String tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        String logged = call(admin, post("/api/businesses/" + barId + "/activities").content("""
                {"type":"VISIT","result":"SPONTANEOUS_VISIT","results":["SPONTANEOUS_VISIT","SAMPLES_LEFT","SAMPLES_LEFT_MORE"],
                 "resultNote":"ბარმენმა თქვა, ხვალ მოდიო",
                 "nextTask":{"type":"DELIVERY","dueAt":"%s","title":"მიტანა","flavorIds":[%d,%d]}}"""
                .formatted(tomorrow, mangoId, vanillaId)), 200);
        Integer activityId = JsonPath.read(logged, "$.id");
        List<String> results = JsonPath.read(logged, "$.results");
        assertThat(results).containsExactly("SPONTANEOUS_VISIT", "SAMPLES_LEFT", "SAMPLES_LEFT_MORE");
        assertThat((String) JsonPath.read(logged, "$.resultNote")).isEqualTo("ბარმენმა თქვა, ხვალ მოდიო");

        // The delivery knows what to take along.
        String detail = call(admin, get("/api/businesses/" + barId), 200);
        assertThat((String) JsonPath.read(detail, "$.openTasks[0].type")).isEqualTo("DELIVERY");
        List<Integer> flavorIds = JsonPath.read(detail, "$.openTasks[0].flavorIds");
        assertThat(flavorIds).containsExactlyInAnyOrder(mangoId, vanillaId);

        // Everything that happened is on the timeline, not only the first result.
        String timeline = call(admin, get("/api/businesses/" + barId + "/timeline"), 200);
        List<String> all = JsonPath.read(timeline, "$[?(@.kind == 'ACTIVITY')].results[*]");
        assertThat(all).contains("SAMPLES_LEFT", "SAMPLES_LEFT_MORE");

        // It went down differently: the entry itself is opened and corrected.
        String reopened = call(admin, get("/api/businesses/" + barId + "/activities/" + activityId), 200);
        assertThat((String) JsonPath.read(reopened, "$.type")).isEqualTo("VISIT");
        String changed = call(admin, put("/api/businesses/" + barId + "/activities/" + activityId).content("""
                {"type":"MEETING","result":"MEETING_SET","results":["MEETING_SET","INTERESTED"],"notes":"შევთანხმდით შეხვედრაზე"}"""), 200);
        assertThat((String) JsonPath.read(changed, "$.type")).isEqualTo("MEETING");
        List<String> after = JsonPath.read(changed, "$.results");
        assertThat(after).containsExactly("MEETING_SET", "INTERESTED");
        assertThat((String) JsonPath.read(changed, "$.resultNote")).isNull();

        // Written down by mistake: it goes, and the delivery it planned stays standing.
        call(admin, delete("/api/businesses/" + barId + "/activities/" + activityId), 204);
        String left = call(admin, get("/api/businesses/" + barId + "/timeline"), 200);
        List<Object> activities = JsonPath.read(left, "$[?(@.kind == 'ACTIVITY')]");
        assertThat(activities).isEmpty();
        List<String> stillOpen = JsonPath.read(call(admin, get("/api/businesses/" + barId), 200), "$.openTasks[*].type");
        assertThat(stillOpen).containsExactly("DELIVERY");
    }

    @Test
    void theSpreadsheetsGuessesAreClearedInOnePress() throws Exception {
        String admin = login("admin", "test-admin-password");
        String headers = """
                ["სავაჭრო დასახელება","ტელეფონი","შემდეგი ნაბიჯი"]""";
        String rows = """
                [["Guessed Bar","555 10 20 30","დავურეკოთ 20/9"]]""";
        String body = """
                {"headers":%s,"rows":%s,"mapping":{"0":"NAME","1":"PHONE","2":"NEXT_STEP"},"source":"guesses.xlsx",
                 "options":{"skipDuplicates":true,"createFollowUps":true,"city":"თბილისი"}}""".formatted(headers, rows);
        assertThat((Integer) JsonPath.read(call(admin, post("/api/import/commit").content(body), 200), "$.tasks")).isEqualTo(1);

        Integer id = JsonPath.read(call(admin, get("/api/businesses?q=Guessed Bar"), 200), "$.items[0].id");
        List<Boolean> imported = JsonPath.read(call(admin, get("/api/businesses/" + id), 200), "$.openTasks[*].imported");
        assertThat(imported).containsExactly(true);

        int cancelled = JsonPath.read(call(admin, post("/api/tasks/imported/cancel").content("{}"), 200), "$.cancelled");
        assertThat(cancelled).isGreaterThanOrEqualTo(1);
        List<Object> open = JsonPath.read(call(admin, get("/api/businesses/" + id), 200), "$.openTasks[*]");
        assertThat(open).isEmpty();
    }

    @Test
    void aTaskCanBePushedToAnotherDay() throws Exception {
        String admin = login("admin", "test-admin-password");
        Integer barId = JsonPath.read(call(admin, post("/api/businesses").content("""
                {"name":"Pushed Bar"}"""), 200), "$.id");

        String today = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String hourLater = Instant.parse(today).plus(1, ChronoUnit.HOURS).toString();
        String created = call(admin, post("/api/tasks").content("""
                {"businessId":%d,"type":"MEETING","title":"Tasting","dueAt":"%s","endAt":"%s"}""".formatted(barId, today, hourLater)), 200);
        Integer taskId = JsonPath.read(created, "$.id");
        String endAt = JsonPath.read(created, "$.endAt");
        assertThat(Instant.parse(endAt)).isEqualTo(Instant.parse(hourLater));

        // What a swipe to "tomorrow" sends: the new time and nothing else.
        String tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        String moved = call(admin, post("/api/tasks/" + taskId + "/move").content("""
                {"dueAt":"%s"}""".formatted(tomorrow)), 200);
        assertThat(Instant.parse(JsonPath.read(moved, "$.dueAt"))).isEqualTo(Instant.parse(tomorrow));
        assertThat((String) JsonPath.read(moved, "$.title")).isEqualTo("Tasting");
        // The meeting still lasts as long as it did, one day later.
        assertThat(Instant.parse(JsonPath.read(moved, "$.endAt"))).isEqualTo(Instant.parse(endAt).plus(1, ChronoUnit.DAYS));
        assertThat((String) JsonPath.read(moved, "$.status")).isEqualTo("OPEN");
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
