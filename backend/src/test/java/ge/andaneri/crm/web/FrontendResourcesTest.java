package ge.andaneri.crm.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The site's own address and every screen path open the app; API paths never get a page instead of JSON.
 * The test build has no frontend on disk, so the page served is the "being built" one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FrontendResourcesTest {

    @Autowired
    MockMvc mvc;

    @Test
    void theBareAddressOpensTheApp() throws Exception {
        MvcResult root = mvc.perform(get("/")).andReturn();
        assertThat(root.getResponse().getStatus()).isEqualTo(200);
        assertThat(root.getResponse().getForwardedUrl()).isEqualTo("/index.html");

        MvcResult index = mvc.perform(get("/index.html")).andReturn();
        assertThat(index.getResponse().getStatus()).isEqualTo(200);
        assertThat(index.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Andaneri CRM");
    }

    @Test
    void screenPathsOpenTheAppAndOldScriptsAndTheApiDoNot() throws Exception {
        assertThat(mvc.perform(get("/main")).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/businesses/12")).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/assets/old-1234.js")).andReturn().getResponse().getStatus()).isEqualTo(404);
        MvcResult api = mvc.perform(get("/api/businesses")).andReturn();
        assertThat(api.getResponse().getStatus()).isEqualTo(401);
        assertThat(api.getResponse().getContentAsString()).doesNotContain("<html");
    }
}
