package ge.andaneri.crm.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class IcsWriterTest {

    private static final Instant NOW = Instant.parse("2026-09-15T08:00:00Z");

    @Test
    void aMeetingWithAnAlertHalfAnHourBefore() {
        String ics = IcsWriter.calendar("Andaneri CRM", List.of(new IcsWriter.Event("task-7@andaneri-crm",
                Instant.parse("2026-09-15T13:00:00Z"), Instant.parse("2026-09-15T14:00:00Z"), null,
                "შეხვედრა: Bar X", "Chavchavadze 1, Tbilisi", "Giorgi 599 11 22 33\nask about mango", "https://crm/main?open=3", 30)), NOW);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded)
                .contains("UID:task-7@andaneri-crm\r\n")
                .contains("DTSTART:20260915T130000Z\r\n")
                .contains("DTEND:20260915T140000Z\r\n")
                .contains("SUMMARY:შეხვედრა: Bar X\r\n")
                .contains("LOCATION:Chavchavadze 1\\, Tbilisi\r\n")
                .contains("DESCRIPTION:Giorgi 599 11 22 33\\nask about mango\r\n")
                .contains("BEGIN:VALARM\r\nACTION:DISPLAY\r\n")
                .contains("TRIGGER:-PT30M\r\n");
    }

    @Test
    void allDayTasksUseDatesAndAMorningAlert() {
        String ics = IcsWriter.calendar("x", List.of(new IcsWriter.Event("task-8@andaneri-crm", Instant.parse("2026-09-16T08:00:00Z"),
                Instant.parse("2026-09-16T08:30:00Z"), LocalDate.of(2026, 9, 16), "ზარი: Cafe", null, null, null, 30)), NOW);
        assertThat(ics).contains("DTSTART;VALUE=DATE:20260916\r\n").contains("DTEND;VALUE=DATE:20260917\r\n")
                .contains("TRIGGER;RELATED=START:PT9H\r\n");
    }

    @Test
    void noAlertWhenTheTaskHasNone() {
        String ics = IcsWriter.calendar("x", List.of(new IcsWriter.Event("u", NOW, NOW, null, "s", null, null, null, null)), NOW);
        assertThat(ics).doesNotContain("VALARM");
    }

    @Test
    void longGeorgianLinesAreFoldedWithoutBreakingLetters() {
        String text = "გრენადინი, ვანილი, მანგო, ფისტა - ".repeat(10);
        StringBuilder out = new StringBuilder();
        IcsWriter.line(out, "DESCRIPTION:" + IcsWriter.escape(text));

        String[] physical = out.toString().split("\r\n");
        assertThat(physical.length).isGreaterThan(3);
        assertThat(Arrays.stream(physical).mapToInt(l -> l.getBytes(StandardCharsets.UTF_8).length).max().orElse(0)).isLessThanOrEqualTo(75);
        // Unfolding gives back exactly the line (the text's own trailing space included), then its CRLF.
        assertThat(out.toString().replace("\r\n ", "")).isEqualTo("DESCRIPTION:" + IcsWriter.escape(text) + "\r\n");
        assertThat(new String(out.toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)).doesNotContain("�");
    }

    @Test
    void specialCharactersAreEscaped() {
        assertThat(IcsWriter.escape("a;b,c\\d\ne")).isEqualTo("a\\;b\\,c\\\\d\\ne");
    }
}
