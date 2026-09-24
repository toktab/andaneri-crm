package ge.andaneri.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
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
 * What happened in the office: a new colleague got his own password wrong a few times, and the address
 * everyone works from was blocked - which threw the admin out of a session that was already open and
 * refused to let him back in.
 *
 * <p>It must not happen again: fumbling one's own password slows down that account and nobody else, an
 * address is only blocked when someone is guessing at names that do not exist, and the first admin gets
 * in from anywhere regardless - with a password that cannot be changed from the website.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OfficeLockoutTest {

    private static final String OFFICE = "31.146.100.7";

    @Autowired
    MockMvc mvc;

    @Test
    void oneColleaguesWrongPasswordDoesNotShutTheOfficeOut() throws Exception {
        String admin = login("admin", "test-admin-password", OFFICE);
        call(admin, post("/api/admin/users").content("""
                {"username":"friend","fullName":"Friend","role":"SALES","password":"correct-horse-9"}"""), 200);

        // The new colleague tries his password fifteen times - five more than the limit.
        for (int i = 0; i < 15; i++) {
            attempt("friend", "wrong-" + i, OFFICE);
        }

        // The admin's open session still works from that same address.
        call(admin, get("/api/businesses"), 200);
        // And the admin can sign in again from it.
        assertThat(login("admin", "test-admin-password", OFFICE)).isNotBlank();
        // Another colleague on the same address is unaffected.
        call(admin, post("/api/admin/users").content("""
                {"username":"colleague","fullName":"Colleague","role":"SALES","password":"correct-horse-8"}"""), 200);
        assertThat(login("colleague", "correct-horse-8", OFFICE)).isNotBlank();

        // The address was never blocked; only the fumbling account is asked to wait.
        List<Object> blocks = JsonPath.read(call(admin, get("/api/admin/ip-blocks"), 200), "$[*]");
        assertThat(blocks).isEmpty();
        assertThat(status(attempt("friend", "wrong-again", OFFICE))).isEqualTo(429);
        // ...and his right password still gets refused only until the window passes, not the whole office.
        assertThat(status(attempt("colleague", "correct-horse-8", OFFICE))).isEqualTo(200);
    }

    @Test
    void guessingAtNamesBlocksTheAddressButNeverTheFirstAdmin() throws Exception {
        String stranger = "45.12.99.3";
        String admin = login("admin", "test-admin-password", OFFICE);

        for (int i = 0; i < 12; i++) {
            attempt("ghost" + i, "letmein", stranger);
        }

        // That address is turned away now.
        String blocks = call(admin, get("/api/admin/ip-blocks"), 200);
        List<String> patterns = JsonPath.read(blocks, "$[*].pattern");
        assertThat(patterns).contains(stranger);
        MvcResult refused = mvc.perform(get("/api/businesses").header("Authorization", "Bearer " + admin).with(from(stranger))).andReturn();
        assertThat(refused.getResponse().getStatus()).isIn(200, 403);

        // The first admin still gets in from that very address: it is the way back to lift the block.
        assertThat(login("admin", "test-admin-password", stranger)).isNotBlank();

        // An admin can lift it without hunting for root.
        Integer id = JsonPath.read(blocks, "$[0].id");
        call(admin, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/ip-blocks/" + id), 204);
        List<Object> left = JsonPath.read(call(admin, get("/api/admin/ip-blocks"), 200), "$[*]");
        assertThat(left).isEmpty();
    }

    @Test
    void theFirstAdminsPasswordCannotBeChangedFromTheWebsite() throws Exception {
        String admin = login("admin", "test-admin-password", OFFICE);
        // Not by the admin themselves...
        call(admin, post("/api/auth/password").content("""
                {"currentPassword":"test-admin-password","newPassword":"something-else-1"}"""), 400);
        // ...and not through the user screen either.
        List<Integer> ids = JsonPath.read(call(admin, get("/api/admin/users"), 200), "$[?(@.username == 'admin')].id");
        Integer id = ids.get(0);
        call(admin, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/" + id).content("""
                {"fullName":"Administrator","role":"ADMIN","active":true,"password":"something-else-1"}"""), 400);
        // Nor switched off.
        call(admin, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/" + id).content("""
                {"fullName":"Administrator","role":"ADMIN","active":false}"""), 400);
        // It still signs in with the password from the environment.
        assertThat(login("admin", "test-admin-password", OFFICE)).isNotBlank();
    }

    // ----------------------------------------------------------------- helpers

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private MvcResult attempt(String username, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).with(from(ip))
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private String login(String username, String password, String ip) throws Exception {
        MvcResult result = attempt(username, password, ip);
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(body).isEqualTo(200);
        return JsonPath.read(body, "$.token");
    }

    private String call(String token, MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + token).with(from(OFFICE))
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body;
    }
}
