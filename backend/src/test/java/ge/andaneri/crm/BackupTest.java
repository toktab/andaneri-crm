package ge.andaneri.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import ge.andaneri.crm.backup.BackupService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
 * A backup holds everything, every download is logged with who and from where, other admins can download
 * it while it is kept, it converts to Excel, salespeople cannot touch it, and it is gone after the retention.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackupTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    BackupService backups;

    @Test
    void aBackupHoldsEverythingIsLoggedConvertsToExcelAndExpires() throws Exception {
        String admin = login("admin", "test-admin-password");
        Integer barId = JsonPath.read(call(admin, post("/api/businesses").content("""
                {"name":"Backup Bar","phone":"+995 555 90 90 90"}"""), 200), "$.id");
        call(admin, post("/api/businesses/" + barId + "/contacts").content("""
                {"name":"Levan","roleTitle":"owner"}"""), 200);

        String created = call(admin, post("/api/admin/backups"), 200);
        Integer id = JsonPath.read(created, "$.id");
        assertThat((String) JsonPath.read(created, "$.fileName")).startsWith("andaneri-backup-").endsWith(".zip");

        MvcResult download = mvc.perform(get("/api/admin/backups/" + id + "/download").header("Authorization", "Bearer " + admin)
                .with(r -> { r.setRemoteAddr("10.20.30.40"); return r; })).andReturn();
        assertThat(download.getResponse().getStatus()).isEqualTo(200);
        Map<String, String> files = unzip(download.getResponse().getContentAsByteArray());
        assertThat(files.keySet()).anyMatch(n -> n.endsWith("/README.txt")).anyMatch(n -> n.endsWith("/backup.json"))
                .anyMatch(n -> n.endsWith("/businesses-import.json"));
        String backupJson = files.entrySet().stream().filter(e -> e.getKey().endsWith("/backup.json")).findFirst().orElseThrow().getValue();
        assertThat(backupJson).contains("\"format\" : \"andaneri-crm-backup\"").contains("Backup Bar").contains("Levan")
                .contains("\"catalog\"").doesNotContain("passwordHash");

        // Logged: made by admin, downloaded by admin from 10.20.30.40.
        String overview = call(admin, get("/api/admin/backups"), 200);
        List<String> actions = JsonPath.read(overview, "$.backups[?(@.id == " + id + ")].events[*].action");
        assertThat(actions).contains("CREATED", "DOWNLOADED");
        List<String> ips = JsonPath.read(overview, "$.backups[?(@.id == " + id + ")].events[?(@.action == 'DOWNLOADED')].ip");
        assertThat(ips).contains("10.20.30.40");
        assertThat((Boolean) JsonPath.read(overview, "$.status.due")).isFalse();

        // Into Excel, sheet per kind of data.
        MvcResult excel = mvc.perform(multipart("/api/admin/backups/convert")
                .file(new MockMultipartFile("file", "backup.zip", "application/zip", download.getResponse().getContentAsByteArray()))
                .param("lang", "en").header("Authorization", "Bearer " + admin)).andReturn();
        assertThat(excel.getResponse().getStatus()).isEqualTo(200);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel.getResponse().getContentAsByteArray()))) {
            assertThat(workbook.getSheet("Businesses")).isNotNull();
            assertThat(workbook.getSheet("Contacts")).isNotNull();
            assertThat(workbook.getSheet("Products").getLastRowNum()).isGreaterThan(10);
            boolean found = false;
            for (var row : workbook.getSheet("Businesses")) {
                found |= row.getCell(2) != null && "Backup Bar".equals(row.getCell(2).getStringCellValue());
            }
            assertThat(found).isTrue();
        }

        // Salespeople cannot see or download backups.
        call(admin, post("/api/admin/users").content("""
                {"username":"backupsales","fullName":"Backup Sales","role":"SALES","password":"password-123"}"""), 200);
        String sales = login("backupsales", "password-123");
        call(sales, get("/api/admin/backups"), 403);
        call(sales, get("/api/admin/backups/" + id + "/download"), 403);

        // The retention is a setting; past it the file is gone, the log stays.
        call(admin, put("/api/admin/settings").content("{\"backup_retention_days\":30,\"backup_interval_days\":3}"), 200);
        String status = call(admin, get("/api/admin/backups/status"), 200);
        assertThat((Integer) JsonPath.read(status, "$.retentionDays")).isEqualTo(30);
        assertThat((Integer) JsonPath.read(status, "$.intervalDays")).isEqualTo(3);
        call(admin, put("/api/admin/settings").content("{\"backup_retention_days\":14,\"backup_interval_days\":7}"), 200);

        assertThat(backups.cleanup(Instant.now().plus(Duration.ofDays(15)))).isGreaterThanOrEqualTo(1);
        String gone = call(admin, get("/api/admin/backups/" + id + "/download"), 404);
        assertThat((String) JsonPath.read(gone, "$.code")).isEqualTo("BACKUP_EXPIRED");
        List<String> log = JsonPath.read(call(admin, get("/api/admin/backups"), 200), "$.log[*].action");
        assertThat(log).contains("EXPIRED", "DOWNLOADED", "CREATED");
    }

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
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
}
