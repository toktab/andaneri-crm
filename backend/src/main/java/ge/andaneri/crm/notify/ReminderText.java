package ge.andaneri.crm.notify;

import ge.andaneri.crm.domain.QuickNote;
import ge.andaneri.crm.domain.Task;
import ge.andaneri.crm.domain.TaskType;
import ge.andaneri.crm.notify.PushService.Message;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The words of a reminder, in Georgian or English: "17:00 · შეხვედრა · Bar X" / "Giorgi · +995 ... · notes". */
final class ReminderText {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<TaskType, String[]> TYPE = Map.of(
            TaskType.CALL, new String[] {"ზარი", "Call"},
            TaskType.MEETING, new String[] {"შეხვედრა", "Meeting"},
            TaskType.VISIT, new String[] {"ვიზიტი", "Visit"},
            TaskType.SEND_SAMPLES, new String[] {"სემპლების გაგზავნა", "Send samples"},
            TaskType.SEND_PRICE_LIST, new String[] {"ფასების გაგზავნა", "Send price list"},
            TaskType.FOLLOW_UP, new String[] {"გადაკავშირება", "Follow up"},
            TaskType.CHECK_REORDER, new String[] {"ხელახალი შეკვეთა", "Check reorder"},
            TaskType.OTHER, new String[] {"სხვა", "Other"});

    private ReminderText() {
    }

    static String typeLabel(TaskType type, boolean en) {
        return TYPE.getOrDefault(type, TYPE.get(TaskType.OTHER))[en ? 1 : 0];
    }

    static Message task(Task t, String lang, ZoneId zone) {
        boolean en = "en".equals(lang);
        String when = t.isAllDay() ? (en ? "Today" : "დღეს") : TIME.format(t.getDueAt().atZone(zone));
        String what = TYPE.getOrDefault(t.getType(), TYPE.get(TaskType.OTHER))[en ? 1 : 0];
        String who = t.getBusiness() != null ? t.getBusiness().getName() : t.getTitle();
        String title = when + " · " + what + (who == null ? "" : " · " + who);

        List<String> body = new ArrayList<>();
        if (t.getContact() != null) {
            body.add(t.getContact().getName());
        }
        String phone = t.getContact() != null && t.getContact().getPhone() != null ? t.getContact().getPhone()
                : t.getBusiness() != null ? t.getBusiness().getPhone() : null;
        if (phone != null) {
            body.add(phone);
        }
        if (t.getBusiness() != null && t.getTitle() != null) {
            body.add(t.getTitle());
        }
        String place = t.getLocation() != null ? t.getLocation() : t.getBusiness() != null ? t.getBusiness().getAddress() : null;
        if (place != null && (t.getType() == TaskType.MEETING || t.getType() == TaskType.VISIT)) {
            body.add(place);
        }
        if (t.getNotes() != null) {
            body.add(cut(t.getNotes().strip().lines().findFirst().orElse(""), 120));
        }
        String url = t.getBusiness() != null ? "/main?open=" + t.getBusiness().getId() : "/tasks";
        return new Message(title, String.join(" · ", body), url, "task-" + t.getId());
    }

    static Message note(QuickNote n, String lang) {
        boolean en = "en".equals(lang);
        String about = n.getBusiness() == null ? "" : " · " + n.getBusiness().getName();
        return new Message((en ? "Reminder" : "შეხსენება") + about, cut(n.getBody(), 180), "/notes", "note-" + n.getId());
    }

    static Message test(String lang) {
        boolean en = "en".equals(lang);
        return new Message(en ? "Andaneri CRM: notifications work" : "Andaneri CRM: შეხსენებები მუშაობს",
                en ? "Meetings and calls will be reminded here." : "შეხვედრები და ზარები აქ შეგახსენდება.", "/", "test");
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
