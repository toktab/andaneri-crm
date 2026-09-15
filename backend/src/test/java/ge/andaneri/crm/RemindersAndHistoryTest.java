package ge.andaneri.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import ge.andaneri.crm.notify.PushService;
import ge.andaneri.crm.notify.ReminderScheduler;
import ge.andaneri.crm.security.SecurityLog;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Reminders go out once, at the right time, and again after a task is moved; devices can only register real
 * push services; the calendar feed needs its secret; and each person's history shows their own changes.
 * Push delivery itself is replaced by a mock here (WebPushTest covers the encryption).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RemindersAndHistoryTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReminderScheduler scheduler;

    @Autowired
    SecurityLog securityLog;

    @MockitoBean
    PushService push;

    @Test
    void aTaskIsRemindedOnceAtItsTimeAndAgainAfterItIsMoved() throws Exception {
        String admin = login("admin", "test-admin-password");
        Integer barId = JsonPath.read(call(admin, post("/api/businesses").content("""
                {"name":"Reminder Bar","phone":"+995 555 70 70 70"}"""), 200), "$.id");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // In 20 minutes, default reminder 30 minutes before: due now.
        Integer soon = JsonPath.read(call(admin, post("/api/tasks").content("""
                {"type":"MEETING","businessId":%d,"title":"Tasting","dueAt":"%s"}""".formatted(barId, now.plus(Duration.ofMinutes(20)))), 200), "$.id");
        // In 2 hours: not yet.
        call(admin, post("/api/tasks").content("""
                {"type":"CALL","businessId":%d,"title":"Later call","dueAt":"%s"}""".formatted(barId, now.plus(Duration.ofHours(2)))), 200);
        // Asked for no reminder.
        call(admin, post("/api/tasks").content("""
                {"type":"VISIT","businessId":%d,"title":"Silent visit","dueAt":"%s","remindMinutes":0}""".formatted(barId, now.plus(Duration.ofMinutes(5)))), 200);

        scheduler.runOnce(now);
        assertThat(titlesSent()).anyMatch(t -> t.contains("Reminder Bar") && t.contains("Meeting"))
                .noneMatch(t -> t.contains("Visit") && t.contains("Reminder Bar"))
                .noneMatch(t -> t.contains("Call") && t.contains("Reminder Bar"));
        assertThat(bodiesSent()).anyMatch(b -> b.contains("+995 555 70 70 70") && b.contains("Tasting"));

        clearInvocations(push);
        scheduler.runOnce(now.plusSeconds(60));
        assertThat(titlesSent()).noneMatch(t -> t.contains("Reminder Bar"));

        // The call two hours ahead comes up 30 minutes before it.
        scheduler.runOnce(now.plus(Duration.ofMinutes(91)));
        assertThat(titlesSent()).anyMatch(t -> t.contains("Reminder Bar") && t.contains("Call"));

        // Moving the meeting makes it remind again.
        clearInvocations(push);
        call(admin, put("/api/tasks/" + soon).content("""
                {"type":"MEETING","businessId":%d,"title":"Tasting","dueAt":"%s","remindMinutes":60}""".formatted(barId, now.plus(Duration.ofMinutes(50)))), 200);
        scheduler.runOnce(now.plusSeconds(120));
        assertThat(titlesSent()).anyMatch(t -> t.contains("Reminder Bar") && t.contains("Meeting"));
    }

    @Test
    void devicesRegisterOnlyWithRealPushServicesAndEveryoneSetsTheirOwnTime() throws Exception {
        String admin = login("admin", "test-admin-password");
        call(admin, post("/api/push/subscriptions").content("""
                {"endpoint":"https://mysql.railway.internal/steal","keys":{"p256dh":"x","auth":"y"}}"""), 400);
        String status = call(admin, post("/api/push/subscriptions").content("""
                {"endpoint":"https://fcm.googleapis.com/fcm/send/device-1","keys":{"p256dh":"BPublicKey","auth":"authsecret"},"lang":"en"}"""), 200);
        assertThat(((Number) JsonPath.read(status, "$.devices")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat((String) JsonPath.read(status, "$.publicKey")).hasSizeGreaterThan(80);

        String removed = call(admin, post("/api/push/subscriptions/remove").content("""
                {"endpoint":"https://fcm.googleapis.com/fcm/send/device-1"}"""), 200);
        assertThat(((Number) JsonPath.read(removed, "$.devices")).intValue()).isZero();

        String settings = call(admin, put("/api/me/reminders").content("{\"reminderMinutes\":45}"), 200);
        assertThat((Integer) JsonPath.read(settings, "$.reminderMinutes")).isEqualTo(45);
        call(admin, put("/api/me/reminders").content("{\"reminderMinutes\":30}"), 200);
        call(admin, put("/api/me/reminders").content("{\"reminderMinutes\":-5}"), 400);
    }

    @Test
    void theCalendarFeedNeedsItsSecretAddress() throws Exception {
        String admin = login("admin", "test-admin-password");
        Integer barId = JsonPath.read(call(admin, post("/api/businesses").content("""
                {"name":"Calendar Lounge"}"""), 200), "$.id");
        Integer taskId = JsonPath.read(call(admin, post("/api/tasks").content("""
                {"type":"MEETING","businessId":%d,"dueAt":"%s","location":"Vake, Tbilisi"}"""
                .formatted(barId, Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS))), 200), "$.id");

        String token = JsonPath.read(call(admin, post("/api/me/calendar"), 200), "$.token");
        MvcResult feed = mvc.perform(get("/cal/" + token + ".ics")).andReturn();
        assertThat(feed.getResponse().getStatus()).isEqualTo(200);
        assertThat(feed.getResponse().getContentType()).startsWith("text/calendar");
        String ics = feed.getResponse().getContentAsString(StandardCharsets.UTF_8).replace("\r\n ", "");
        assertThat(ics).contains("UID:task-" + taskId + "@andaneri-crm").contains("Calendar Lounge").contains("LOCATION:Vake\\, Tbilisi")
                .contains("TRIGGER:-PT30M");

        assertThat(mvc.perform(get("/cal/not-the-real-token-000000.ics")).andReturn().getResponse().getStatus()).isEqualTo(404);

        String path = JsonPath.read(call(admin, post("/api/tasks/" + taskId + "/calendar-link"), 200), "$.path");
        assertThat(mvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("BEGIN:VEVENT");

        // A new address cuts the old one off.
        String newToken = JsonPath.read(call(admin, post("/api/me/calendar"), 200), "$.token");
        assertThat(newToken).isNotEqualTo(token);
        assertThat(mvc.perform(get("/cal/" + token + ".ics")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void myHistoryShowsWhatIChangedFromWhatToWhat() throws Exception {
        String admin = login("admin", "test-admin-password");
        Integer id = JsonPath.read(call(admin, post("/api/businesses").content("""
                {"name":"History Cafe","phone":"+995 555 10 10 10"}"""), 200), "$.id");
        call(admin, patch("/api/businesses/" + id).content("""
                {"changes":{"phone":"+995 555 20 20 20"}}"""), 200);
        call(admin, post("/api/businesses/" + id + "/contacts").content("""
                {"name":"Nata","roleTitle":"manager"}"""), 200);
        securityLog.flush();

        String history = call(admin, get("/api/history"), 200);
        List<List<String>> phone = JsonPath.read(history, "$.items[?(@.entity == 'Business' && @.action == 'UPDATE' && @.businessId == " + id + ")].changes.phone");
        assertThat(phone).isNotEmpty();
        assertThat(phone.get(0)).containsExactly("+995 555 10 10 10", "+995 555 20 20 20");
        List<String> added = JsonPath.read(history, "$.items[?(@.entity == 'Business' && @.action == 'INSERT' && @.businessId == " + id + ")].objectName");
        assertThat(added).contains("History Cafe");
        List<String> contact = JsonPath.read(history, "$.items[?(@.entity == 'Contact' && @.businessId == " + id + ")].businessName");
        assertThat(contact).contains("History Cafe");
        List<String> contactName = JsonPath.read(history, "$.items[?(@.entity == 'Contact' && @.businessId == " + id + ")].objectName");
        assertThat(contactName).contains("Nata");

        // A salesperson sees only their own.
        call(admin, post("/api/admin/users").content("""
                {"username":"historysales","fullName":"History Sales","role":"SALES","password":"password-123"}"""), 200);
        String sales = login("historysales", "password-123");
        Integer adminId = JsonPath.read(call(admin, get("/api/auth/me"), 200), "$.id");
        call(sales, get("/api/history?userId=" + adminId), 403);
        assertThat((List<Object>) JsonPath.read(call(sales, get("/api/history"), 200), "$.items")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<PushService.Message> sent() {
        return mockingDetails(push).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("sendToUser"))
                .map(i -> ((Function<String, PushService.Message>) i.getArgument(1)).apply("en"))
                .toList();
    }

    private List<String> titlesSent() {
        return sent().stream().map(PushService.Message::title).toList();
    }

    private List<String> bodiesSent() {
        return sent().stream().map(PushService.Message::body).toList();
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
