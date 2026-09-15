package ge.andaneri.crm.notify;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * iCalendar (RFC 5545) text that iPhone, Mac, Google and Outlook calendars read: lines end in CRLF, long
 * lines are folded at 75 bytes without splitting a Georgian letter, and text is escaped. Times are UTC.
 */
public final class IcsWriter {

    /**
     * One calendar entry. {@code day} is set for all-day entries instead of start and end.
     * {@code alarm}: minutes before the start to alert, or null for none; for all-day entries the alert is
     * at {@link ReminderScheduler#ALL_DAY_AT} on the day.
     */
    public record Event(String uid, Instant start, Instant end, LocalDate day, String summary, String location,
            String description, String url, Integer alarm) {
    }

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private IcsWriter() {
    }

    public static String calendar(String name, List<Event> events, Instant now) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//Andaneri CRM//Reminders//EN");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:" + escape(name));
        // Hints for apps that honour them; iPhone and Google decide the real refresh on their own.
        line(out, "REFRESH-INTERVAL;VALUE=DURATION:PT15M");
        line(out, "X-PUBLISHED-TTL:PT15M");
        for (Event event : events) {
            line(out, "BEGIN:VEVENT");
            line(out, "UID:" + event.uid());
            line(out, "DTSTAMP:" + UTC.format(now));
            if (event.day() != null) {
                line(out, "DTSTART;VALUE=DATE:" + DATE.format(event.day()));
                line(out, "DTEND;VALUE=DATE:" + DATE.format(event.day().plusDays(1)));
            } else {
                line(out, "DTSTART:" + UTC.format(event.start()));
                line(out, "DTEND:" + UTC.format(event.end()));
            }
            line(out, "SUMMARY:" + escape(event.summary()));
            if (event.location() != null) {
                line(out, "LOCATION:" + escape(event.location()));
            }
            if (event.description() != null) {
                line(out, "DESCRIPTION:" + escape(event.description()));
            }
            if (event.url() != null) {
                line(out, "URL:" + event.url());
            }
            if (event.alarm() != null) {
                line(out, "BEGIN:VALARM");
                line(out, "ACTION:DISPLAY");
                line(out, "DESCRIPTION:" + escape(event.summary()));
                line(out, event.day() != null
                        ? "TRIGGER;RELATED=START:PT" + ReminderScheduler.ALL_DAY_AT.getHour() + "H"
                        : "TRIGGER:-PT" + event.alarm() + "M");
                line(out, "END:VALARM");
            }
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    /** Appends one content line, folded so no physical line is longer than 75 bytes. */
    static void line(StringBuilder out, String content) {
        int bytes = 0;
        int limit = 75;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > limit) {
                out.append("\r\n ");
                bytes = 0;
                limit = 74; // the leading space of a continuation line counts
            }
            out.appendCodePoint(codePoint);
            bytes += size;
            i += Character.charCount(codePoint);
        }
        out.append("\r\n");
    }
}
