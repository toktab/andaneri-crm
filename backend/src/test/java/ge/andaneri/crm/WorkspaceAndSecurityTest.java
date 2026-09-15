package ge.andaneri.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import ge.andaneri.crm.security.SecurityLog;
import java.nio.charset.StandardCharsets;
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
 * The root account and what it can see (sign-ins, failed passwords, every change, IP rules), and the
 * workspace: projects, their sheets, custom fields, the spreadsheet view with inline and bulk edits.
 * Requests that must come from somewhere else than this machine set their own remote address, because
 * 127.0.0.1 is never blocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceAndSecurityTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SecurityLog securityLog;

    @Test
    void rootSeesFailedSignInsAndEveryChange() throws Exception {
        String root = login("root", "root-test-password", "127.0.0.1");
        String admin = login("admin", "test-admin-password", "127.0.0.1");

        // Root is its own role; an admin can neither open the security centre nor see the root user.
        assertThat((String) JsonPath.read(call(root, get("/api/auth/me"), 200), "$.role")).isEqualTo("ROOT");
        call(admin, get("/api/root/overview"), 403);
        List<String> visible = JsonPath.read(call(admin, get("/api/admin/users"), 200), "$[*].username");
        assertThat(visible).contains("admin").doesNotContain("root");
        // Its password lives in the environment, not in the app.
        call(root, post("/api/auth/password").content("""
                {"currentPassword":"root-test-password","newPassword":"something-else-1"}"""), 400);

        // A wrong password from somewhere: logged with the address, the password itself masked.
        MvcResult failed = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrongpass1\"}")
                .with(from("10.1.1.1"))).andReturn();
        assertThat(failed.getResponse().getStatus()).isEqualTo(401);
        String logins = call(root, get("/api/root/logins?success=false&ip=10.1.1.1"), 200);
        assertThat((String) JsonPath.read(logins, "$.items[0].reason")).isEqualTo("BAD_PASSWORD");
        assertThat((String) JsonPath.read(logins, "$.items[0].username")).isEqualTo("admin");
        String attempted = JsonPath.read(logins, "$.items[0].attemptedPassword");
        assertThat(attempted).isNotEqualTo("wrongpass1").doesNotContain("rongpass");

        // An admin adds a business, then edits it: both show up with who did it and what changed.
        Integer id = JsonPath.read(call(admin, post("/api/businesses").content("""
                {"name":"Audited Cafe","phone":"+995 577 00 11 22"}"""), 200), "$.id");
        call(admin, patch("/api/businesses/" + id).content("""
                {"changes":{"district":"Vake"}}"""), 200);
        securityLog.flush();
        String changes = call(root, get("/api/root/changes?entity=Business&businessId=" + id), 200);
        List<String> actions = JsonPath.read(changes, "$.items[*].action");
        assertThat(actions).contains("INSERT", "UPDATE");
        List<String> who = JsonPath.read(changes, "$.items[?(@.action == 'UPDATE')].username");
        assertThat(who).contains("admin");
        List<String> diffs = JsonPath.read(changes, "$.items[?(@.action == 'UPDATE')].changes");
        assertThat(diffs).anyMatch(d -> d.contains("district") && d.contains("Vake"));

        // Every API call is on record too.
        assertThat(((Number) JsonPath.read(call(root, get("/api/root/requests?method=PATCH"), 200), "$.total")).longValue())
                .isGreaterThanOrEqualTo(1);
        assertThat((String) JsonPath.read(call(root, get("/api/root/overview"), 200), "$.yourIp")).isEqualTo("127.0.0.1");

        // Ending someone's sessions makes their token useless at once.
        Integer adminId = JsonPath.read(call(admin, get("/api/auth/me"), 200), "$.id");
        call(root, post("/api/root/users/" + adminId + "/revoke-sessions"), 204);
        call(admin, get("/api/lookups"), 401);
    }

    @Test
    void tooManyFailuresBlockTheAddressUntilRootLiftsIt() throws Exception {
        String root = login("root", "root-test-password", "127.0.0.1");
        String admin = login("admin", "test-admin-password", "127.0.0.1");
        String ip = "10.2.2.2";
        for (int i = 0; i < 10; i++) {
            int status = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nobody\",\"password\":\"guess" + i + "\"}").with(from(ip)))
                    .andReturn().getResponse().getStatus();
            assertThat(status).isEqualTo(401);
        }
        // The tenth failure blocked the address: from there on nothing gets through, not even a valid token.
        MvcResult blocked = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"test-admin-password\"}").with(from(ip))).andReturn();
        assertThat(blocked.getResponse().getStatus()).isEqualTo(403);
        assertThat(blocked.getResponse().getContentAsString()).contains("IP_BLOCKED");
        call(admin, get("/api/lookups").with(from(ip)), 403);

        String rules = call(root, get("/api/root/ip-rules"), 200);
        List<Integer> blocks = JsonPath.read(rules, "$[?(@.pattern == '10.2.2.2' && @.kind == 'BLOCK')].id");
        assertThat(blocks).hasSize(1);
        call(root, delete("/api/root/ip-rules/" + blocks.get(0)), 204);
        call(admin, get("/api/lookups").with(from(ip)), 200);
    }

    @Test
    void theWhitelistLetsOnlyListedAddressesInExceptRoot() throws Exception {
        String root = login("root", "root-test-password", "127.0.0.1");
        String admin = login("admin", "test-admin-password", "127.0.0.1");
        call(root, post("/api/root/ip-rules").content("""
                {"pattern":"not an ip","kind":"ALLOW"}"""), 400);
        call(root, put("/api/root/settings").content("{\"ip_whitelist_enabled\":1}"), 200);
        try {
            call(admin, get("/api/lookups").with(from("10.3.3.3")), 403);
            call(root, get("/api/root/overview").with(from("10.3.3.3")), 200);
            call(root, post("/api/root/ip-rules").content("""
                    {"pattern":"10.3.0.0/16","kind":"ALLOW","note":"office"}"""), 200);
            call(admin, get("/api/lookups").with(from("10.3.3.3")), 200);
            call(admin, get("/api/lookups").with(from("10.4.4.4")), 403);
        } finally {
            call(root, put("/api/root/settings").content("{\"ip_whitelist_enabled\":0}"), 200);
        }
        call(admin, get("/api/lookups").with(from("10.4.4.4")), 200);
    }

    @Test
    void projectsSheetsCustomFieldsAndTheSpreadsheet() throws Exception {
        String admin = login("admin", "test-admin-password", "127.0.0.1");

        Integer vake = JsonPath.read(call(admin, post("/api/workbooks").content("""
                {"name":"Vake file"}"""), 200), "$.id");
        Integer saburtalo = JsonPath.read(call(admin, post("/api/workbooks").content("""
                {"name":"Saburtalo file"}"""), 200), "$.id");
        Integer bars = JsonPath.read(call(admin, post("/api/sheets").content("""
                {"name":"Bars","workbookId":%d}""".formatted(vake)), 200), "$.id");
        Integer cafes = JsonPath.read(call(admin, post("/api/sheets").content("""
                {"name":"Cafes","workbookId":%d}""".formatted(saburtalo)), 200), "$.id");

        // A custom field is found again by its name rather than added twice.
        Integer instagram = JsonPath.read(call(admin, post("/api/custom-fields").content("""
                {"label":"Instagram"}"""), 200), "$.id");
        assertThat((Integer) JsonPath.read(call(admin, post("/api/custom-fields").content("""
                {"label":"instagram"}"""), 200), "$.id")).isEqualTo(instagram);

        String created = call(admin, post("/api/businesses").content("""
                {"name":"Grid Bar","phone":"+995 591 00 00 01","sheetId":%d,"customValues":{"%d":"@gridbar"}}"""
                .formatted(bars, instagram)), 200);
        Integer barId = JsonPath.read(created, "$.id");
        assertThat((String) JsonPath.read(created, "$.sheetName")).isEqualTo("Bars");
        assertThat((String) JsonPath.read(created, "$.workbookName")).isEqualTo("Vake file");
        assertThat((String) JsonPath.read(created, "$.customValues['" + instagram + "']")).isEqualTo("@gridbar");
        Integer cafeId = JsonPath.read(call(admin, post("/api/businesses").content("""
                {"name":"Grid Cafe","phone":"+995 591 00 00 02"}"""), 200), "$.id");

        // The spreadsheet view, one sheet at a time.
        String grid = call(admin, get("/api/businesses/grid?sheetId=" + bars), 200);
        assertThat((Integer) JsonPath.read(grid, "$.total")).isEqualTo(1);
        assertThat((String) JsonPath.read(grid, "$.items[0].customValues['" + instagram + "']")).isEqualTo("@gridbar");
        Integer version = JsonPath.read(grid, "$.items[0].version");

        // One cell at a time; an edit based on an old copy of the row is refused.
        String patched = call(admin, patch("/api/businesses/" + barId).content("""
                {"changes":{"phone":"+995 591 99 99 99","custom.%d":"@newhandle","priority":"HIGH"},"version":%d}"""
                .formatted(instagram, version)), 200);
        assertThat((String) JsonPath.read(patched, "$.phone")).isEqualTo("+995 591 99 99 99");
        assertThat((String) JsonPath.read(patched, "$.priority")).isEqualTo("HIGH");
        assertThat((String) JsonPath.read(patched, "$.customValues['" + instagram + "']")).isEqualTo("@newhandle");
        call(admin, patch("/api/businesses/" + barId).content("""
                {"changes":{"city":"Batumi"},"version":%d}""".formatted(version)), 409);
        call(admin, patch("/api/businesses/" + barId).content("""
                {"changes":{"status":"NOT_A_STATUS"}}"""), 400);

        // Many rows at once: both into the Cafes sheet.
        String bulk = call(admin, post("/api/businesses/bulk").content("""
                {"ids":[%d,%d],"sheetId":%d,"priority":"LOW"}""".formatted(barId, cafeId, cafes)), 200);
        assertThat((Integer) JsonPath.read(bulk, "$.updated")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(call(admin, get("/api/businesses/grid?workbookId=" + saburtalo), 200), "$.total")).isEqualTo(2);

        // Merging projects moves the sheets; merging sheets moves the rows.
        call(admin, post("/api/workbooks/" + saburtalo + "/merge-into/" + vake), 200);
        String tree = call(admin, get("/api/workbooks/tree"), 200);
        List<String> vakeSheets = JsonPath.read(tree, "$.workbooks[?(@.id == " + vake + ")].sheets[*].name");
        assertThat(vakeSheets).contains("Bars", "Cafes");
        List<Integer> projectIds = JsonPath.read(tree, "$.workbooks[*].id");
        assertThat(projectIds).doesNotContain(saburtalo);
        call(admin, post("/api/sheets/" + cafes + "/merge-into/" + bars), 200);
        assertThat((Integer) JsonPath.read(call(admin, get("/api/businesses/grid?sheetId=" + bars), 200), "$.total")).isEqualTo(2);

        // An import files its rows into a project and sheet named after the file, custom column included.
        String body = """
                {"headers":["Name","Insta"],"rows":[["Imported Pub","@pub"]],"mapping":{"0":"NAME","1":"CUSTOM:%d"},
                 "source":"Old list.xlsx","workbookName":"Old list","sheetName":"Sheet1",
                 "options":{"skipDuplicates":true}}""".formatted(instagram);
        assertThat((Integer) JsonPath.read(call(admin, post("/api/import/commit").content(body), 200), "$.created")).isEqualTo(1);
        Integer pubId = JsonPath.read(call(admin, get("/api/businesses?q=Imported Pub"), 200), "$.items[0].id");
        String pub = call(admin, get("/api/businesses/" + pubId), 200);
        assertThat((String) JsonPath.read(pub, "$.workbookName")).isEqualTo("Old list");
        assertThat((String) JsonPath.read(pub, "$.sheetName")).isEqualTo("Sheet1");
        assertThat((String) JsonPath.read(pub, "$.customValues['" + instagram + "']")).isEqualTo("@pub");

        // The JSON backup carries where it is filed and its custom fields by name.
        String file = mvc.perform(get("/api/export/businesses?format=json&q=Imported Pub").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(file, "$.businesses[0].project")).isEqualTo("Old list");
        assertThat((String) JsonPath.read(file, "$.businesses[0].customFields.Instagram")).isEqualTo("@pub");

        // Deleting a sheet keeps its businesses, just unfiled.
        call(admin, delete("/api/sheets/" + bars), 204);
        assertThat((String) JsonPath.read(call(admin, get("/api/businesses/" + barId), 200), "$.name")).isEqualTo("Grid Bar");
        assertThat((Integer) JsonPath.read(call(admin, get("/api/businesses/grid?unfiled=true&q=Grid"), 200), "$.total")).isEqualTo(2);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String login(String username, String password, String ip) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}").with(from(ip))).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(body).isEqualTo(200);
        return JsonPath.read(body, "$.token");
    }

    private String call(String token, MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body;
    }
}
