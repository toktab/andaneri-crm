package ge.andaneri.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
 * What a supervisor can see: the team's numbers over a period, who is on which place at this moment,
 * a colleague's own day and notes - and, as importantly, that a salesperson sees none of it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupervisorViewTest {

    @Autowired
    MockMvc mvc;

    @Test
    void aSupervisorSeesTheTeamsWorkWhileASalespersonDoesNot() throws Exception {
        String admin = login("admin", "test-admin-password");
        call(admin, post("/api/admin/users").content("""
                {"username":"seller","fullName":"Seller","role":"SALES","password":"password-123"}"""), 200);
        call(admin, post("/api/admin/users").content("""
                {"username":"boss","fullName":"Boss","role":"SUPERVISOR","password":"password-123"}"""), 200);
        String seller = login("seller", "password-123");
        String boss = login("boss", "password-123");

        // The salesperson does a day's work.
        Integer barId = JsonPath.read(call(seller, post("/api/businesses").content("""
                {"name":"Team Bar","phone":"555 71 42 18"}"""), 200), "$.id");
        call(seller, post("/api/businesses/" + barId + "/activities").content("""
                {"type":"CALL","result":"TALKED","notes":"ვისაუბრეთ"}"""), 200);
        call(seller, post("/api/businesses/" + barId + "/activities").content("""
                {"type":"MEETING","result":"SAMPLES_REQUESTED"}"""), 200);
        call(seller, post("/api/notes").content("""
                {"body":"ჩემი პირადი ჩანაწერი"}"""), 200);

        // The supervisor reads the team's numbers and finds that work under the right name.
        String team = call(boss, get("/api/team"), 200);
        List<String> names = JsonPath.read(team, "$.rows[*].user.fullName");
        assertThat(names).contains("Seller", "Boss");
        List<Integer> calls = JsonPath.read(team, "$.rows[?(@.user.fullName == 'Seller')].calls");
        assertThat(calls).containsExactly(1);
        List<Integer> meetings = JsonPath.read(team, "$.rows[?(@.user.fullName == 'Seller')].meetings");
        assertThat(meetings).containsExactly(1);
        List<String> lastSeen = JsonPath.read(team, "$.rows[?(@.user.fullName == 'Seller')].lastActivityAt");
        assertThat(lastSeen).isNotEmpty().allSatisfy(at -> assertThat(at).isNotNull());

        // The salesperson's own day, read as them: their dashboard, their notes.
        List<Integer> sellerIds = JsonPath.read(team, "$.rows[?(@.user.fullName == 'Seller')].user.id");
        long sellerId = sellerIds.get(0);
        String theirDay = call(boss, get("/api/dashboard?userId=" + sellerId), 200);
        assertThat(theirDay).isNotBlank();
        List<String> theirNotes = JsonPath.read(call(boss, get("/api/notes?userId=" + sellerId), 200), "$[*].body");
        assertThat(theirNotes).contains("ჩემი პირადი ჩანაწერი");

        // A salesperson cannot look at any of it.
        call(seller, get("/api/team"), 403);
        call(seller, get("/api/team/now"), 403);
        call(seller, get("/api/notes?userId=" + 1), 403);
    }

    @Test
    void whoIsOnThisPlaceRightNow() throws Exception {
        String admin = login("admin", "test-admin-password");
        call(admin, post("/api/admin/users").content("""
                {"username":"caller","fullName":"Caller","role":"SALES","password":"password-123"}"""), 200);
        String caller = login("caller", "password-123");
        Integer barId = JsonPath.read(call(admin, post("/api/businesses").content("""
                {"name":"Busy Bar"}"""), 200), "$.id");

        // Nobody is on it yet.
        List<Object> before = JsonPath.read(call(admin, get("/api/businesses/" + barId + "/who-is-here"), 200), "$.others[*]");
        assertThat(before).isEmpty();

        // The salesperson opens call mode there.
        call(caller, post("/api/presence").content("{\"businessId\":" + barId + "}"), 200);

        // The admin's screen, and a supervisor's "who is working now", both show it.
        List<String> here = JsonPath.read(call(admin, get("/api/businesses/" + barId + "/who-is-here"), 200), "$.others[*].userName");
        assertThat(here).containsExactly("Caller");
        List<String> onNow = JsonPath.read(call(admin, get("/api/team/now"), 200), "$[*].businessName");
        assertThat(onNow).contains("Busy Bar");

        // ...and stops when they leave.
        call(caller, post("/api/presence/leave"), 200);
        List<Object> after = JsonPath.read(call(admin, get("/api/businesses/" + barId + "/who-is-here"), 200), "$.others[*]");
        assertThat(after).isEmpty();
    }

    @Test
    void theMarketSummarySplitsSyrupsFromPureesAndNamesWhoPoursWhat() throws Exception {
        String admin = login("admin", "test-admin-password");
        String lookups = call(admin, get("/api/lookups"), 200);
        Integer syrup = idOf(lookups, "$.categories[?(@.nameEn == 'Syrup')].id");
        Integer puree = idOf(lookups, "$.categories[?(@.nameEn == 'Fruit puree')].id");
        Integer monin = idOf(lookups, "$.brands[?(@.name == 'Monin')].id");
        Integer mango = idOf(lookups, "$.flavors[?(@.nameEn == 'Mango')].id");

        // The place has an owner, because "who pours mango" is only useful next to whose customer it is -
        // and reading that name is what used to break the list.
        Integer sellerId = idOf(call(admin, get("/api/admin/users"), 200), "$[?(@.username == 'admin')].id");
        Integer oneId = JsonPath.read(call(admin, post("/api/businesses").content("""
                {"name":"Syrup Bar %s","assignedToId":%d}"""
                .formatted(Instant.now().truncatedTo(ChronoUnit.SECONDS).getEpochSecond(), sellerId)), 200), "$.id");
        call(admin, post("/api/businesses/" + oneId + "/usages").content("""
                {"categoryId":%d,"brandId":%d,"flavorIds":[%d]}""".formatted(syrup, monin, mango)), 200);
        call(admin, post("/api/businesses/" + oneId + "/usages").content("""
                {"categoryId":%d,"flavorIds":[%d],"source":"FRESH"}""".formatted(puree, mango)), 200);

        String market = call(admin, get("/api/market"), 200);
        // Mango shows under both categories, counted apart.
        List<String> categories = JsonPath.read(market, "$.categories[*].nameEn");
        assertThat(categories).contains("Syrup", "Fruit puree");
        List<Integer> inSyrup = JsonPath.read(market, "$.categories[?(@.nameEn == 'Syrup')].flavors[?(@.nameEn == 'Mango')].businesses");
        assertThat(inSyrup).isNotEmpty();
        List<String> brands = JsonPath.read(market, "$.brands[*].name");
        assertThat(brands).contains("Monin");

        // And each number opens onto the places behind it.
        String who = call(admin, get("/api/market/who?categoryId=" + syrup + "&flavorId=" + mango), 200);
        List<String> places = JsonPath.read(who, "$[*].name");
        assertThat(places).anyMatch(name -> name.startsWith("Syrup Bar"));
        List<String> owners = JsonPath.read(who, "$[?(@.name =~ /Syrup Bar.*/)].assignedTo.fullName");
        assertThat(owners).isNotEmpty();
        List<String> sources = JsonPath.read(call(admin, get("/api/market/who?categoryId=" + puree + "&flavorId=" + mango), 200), "$[*].source");
        assertThat(sources).contains("FRESH");
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
