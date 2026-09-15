package ge.andaneri.crm.io;

import ge.andaneri.crm.common.Text;
import ge.andaneri.crm.domain.ActivityResult;
import ge.andaneri.crm.domain.ActivityType;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.UsageAnswer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one spreadsheet row the way a person would. Built around the team's "Sales Report Form"
 * (Georgian headers, free text everywhere) but driven by a column mapping, so any sheet works.
 *
 * <p>The rule it never breaks: every history cell survives word for word. Brands, flavors, dates,
 * call results and the pipeline stage are guesses layered on top of the original text, and the
 * activities they become are marked as imported so nobody mistakes a guess for a record.
 */
public final class ImportParser {

    private ImportParser() {
    }

    /** What a spreadsheet column holds. HISTORY, COMMENT and SAMPLES may be given to several columns. */
    public enum Field {
        IGNORE, NAME, LEGAL_NAME, ADDRESS, CITY, DISTRICT, TYPE, PHONE, EMAIL, WEBSITE, ID_CODE, BRANCHES,
        CONTACTS, SYRUP_USAGE, FLAVORS, HISTORY, SAMPLES, CUSTOMER, COMMENT, NEXT_STEP
    }

    // ------------------------------------------------------------------ column mapping

    /** The field a header most likely means. Order matters: "შპს დასახელება" is a legal name, not the name. */
    public static Field guess(String header) {
        String h = header == null ? "" : header.toLowerCase(Locale.ROOT);
        if (h.isBlank()) {
            return Field.IGNORE;
        }
        if (has(h, "შპს", "legal", "company name")) return Field.LEGAL_NAME;
        if (has(h, "დირექტორ", "მენეჯერ", "საკონტაქტ", "contact person", "manager", "owner")) return Field.CONTACTS;
        if (has(h, "ნიმუშ", "sample")) return Field.SAMPLES;
        if (has(h, "სიროფ", "ბრენდ", "brand", "syrup")) return Field.SYRUP_USAGE;
        if (has(h, "სახეობ", "გემო", "flavor", "flavour")) return Field.FLAVORS;
        if (has(h, "კლიენტ", "customer")) return Field.CUSTOMER;
        if (has(h, "ნაბიჯ", "next step", "next action")) return Field.NEXT_STEP;
        if (has(h, "დაკავშირ", "შეხვედრ", "პასუხ", "უკუკავშ", "meeting", "call", "feedback", "answer")) return Field.HISTORY;
        if (has(h, "კომენტ", "შენიშნ", "comment", "note")) return Field.COMMENT;
        if (has(h, "ს.კ", "საიდენტ", "id code", "identification", "tax id")) return Field.ID_CODE;
        if (has(h, "ფილიალ", "branch")) return Field.BRANCHES;
        if (has(h, "მისამართ", "address")) return Field.ADDRESS;
        if (has(h, "ქალაქ", "city")) return Field.CITY;
        if (has(h, "რაიონ", "უბან", "district", "area")) return Field.DISTRICT;
        if (has(h, "ტიპ", "type", "category")) return Field.TYPE;
        if (has(h, "ფოსტ", "email", "e-mail", "მეილ")) return Field.EMAIL;
        if (has(h, "ტელ", "phone", "mobile")) return Field.PHONE;
        if (has(h, "ვებ", "website", "site", "facebook")) return Field.WEBSITE;
        if (has(h, "დასახელ", "სახელწოდ", "name", "business")) return Field.NAME;
        return Field.IGNORE;
    }

    /**
     * Column index to target: a {@link Field} name, or "CUSTOM:&lt;id&gt;" for a custom field. Guessing only
     * ever produces built-in fields; custom targets are chosen on the import screen.
     */
    public static Map<Integer, String> guessMapping(List<String> headers) {
        Map<Integer, String> mapping = new LinkedHashMap<>();
        Set<Field> single = new LinkedHashSet<>();
        for (int i = 0; i < headers.size(); i++) {
            Field field = guess(headers.get(i));
            boolean repeatable = field == Field.HISTORY || field == Field.COMMENT || field == Field.SAMPLES || field == Field.IGNORE;
            // A second column that looks like the name (or phone...) is more likely something else: leave it to the user.
            if (!repeatable && !single.add(field)) {
                field = Field.IGNORE;
            }
            mapping.put(i, field.name());
        }
        return mapping;
    }

    /** The custom field id of a "CUSTOM:&lt;id&gt;" target, or null. */
    static Long customFieldId(String target) {
        if (target == null || !target.startsWith("CUSTOM:")) {
            return null;
        }
        try {
            return Long.valueOf(target.substring("CUSTOM:".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Field fieldOf(String target) {
        try {
            return target == null ? Field.IGNORE : Field.valueOf(target);
        } catch (IllegalArgumentException ex) {
            return Field.IGNORE;
        }
    }

    // ------------------------------------------------------------------ vocabulary

    /** Our flavor and brand names plus the spellings that turn up in field notes. */
    public static final class Vocabulary {

        private final List<Map.Entry<String, String>> flavorKeys = new ArrayList<>();
        private final List<Map.Entry<String, String>> brandKeys = new ArrayList<>();

        /** @param flavors pairs of {Georgian name, English name}; matches come back as the English name */
        public Vocabulary(List<String[]> flavors, List<String> brands) {
            for (String[] flavor : flavors) {
                String en = flavor[1];
                addFlavor(squash(en), en);
                String ka = squash(flavor[0]);
                addFlavor(ka, en);
                // Georgian case endings: პიტნა -> პიტნის, ვანილი -> ვანილის. Match on the stem.
                if (ka.length() > 4 && "აიეოუ".indexOf(ka.charAt(ka.length() - 1)) >= 0) {
                    addFlavor(ka.substring(0, ka.length() - 1), en);
                }
            }
            // Stems that change shape when declined, and spellings from the notes.
            String[][] aliases = {
                    {"ატმ", "Peach"}, {"მსხლ", "Pear"}, {"ალუბლ", "Cherry"}, {"ფორთოხლ", "Orange"},
                    {"პაშენფრუტ", "Passion Fruit"}, {"passionfruit", "Passion Fruit"}, {"კენკრ", "Wild Berries"},
                    {"berries", "Wild Berries"}, {"berry", "Wild Berries"}, {"ბლუკურასაო", "Blue Curacao"},
                    {"მოხიტ", "Mojito"}, {"ჯინჯერ", "Ginger"}, {"ლაიმ", "Lime"}, {"ბაბლგამ", "Bubble Gum"},
                    {"გლინტვაინ", "Mulled Wine"}, {"ტირამის", "Tiramisu"}, {"ფისტაშ", "Pistachio"}};
            for (String[] alias : aliases) {
                addFlavor(alias[0], alias[1]);
            }
            for (String brand : brands) {
                addBrand(squash(brand), brand);
            }
            String[][] brandAliases = {
                    {"1883", "1883 Maison Routin"}, {"მონინ", "Monin"}, {"monin", "Monin"}, {"ბლექსი", "Black Sea"},
                    {"blacksea", "Black Sea"}, {"დოლერ", "Döhler"}, {"dohler", "Döhler"}, {"doehler", "Döhler"},
                    {"ტორანი", "Torani"}, {"torani", "Torani"}, {"დავინჩი", "DaVinci Gourmet"}, {"davinci", "DaVinci Gourmet"},
                    {"ბარინოფ", "Barinoff"}, {"barinoff", "Barinoff"}, {"ბოირონ", "Boiron"}, {"boiron", "Boiron"},
                    {"ponthier", "Ponthier"}, {"ანდანერ", "Andaneri"}, {"andaneri", "Andaneri"}};
            for (String[] alias : brandAliases) {
                addBrand(alias[0], alias[1]);
            }
            // Longest first, so "მარილიანი კარამელი" is found before "კარამელი" can claim part of it.
            flavorKeys.sort(Comparator.comparing((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
            brandKeys.sort(Comparator.comparing((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
        }

        private void addFlavor(String key, String name) {
            if (key.length() >= 3) {
                flavorKeys.add(Map.entry(key, name));
            }
        }

        private void addBrand(String key, String name) {
            if (key.length() >= 3) {
                brandKeys.add(Map.entry(key, name));
            }
        }

        public List<String> flavorsIn(String text) {
            // Words that contain a flavor's letters without meaning it: lemonade, pink, coffees.
            String working = squash(text).replaceAll("ლიმონათ|lemonade|ვარდისფერ|ფორთოხლისყვავილ", "#");
            return find(working, flavorKeys);
        }

        public List<String> brandsIn(String text) {
            return find(squash(text), brandKeys);
        }

        private static List<String> find(String working, List<Map.Entry<String, String>> keys) {
            Set<String> found = new LinkedHashSet<>();
            StringBuilder text = new StringBuilder(working);
            for (Map.Entry<String, String> key : keys) {
                int at = text.indexOf(key.getKey());
                while (at >= 0) {
                    found.add(key.getValue());
                    // Blank the match out so a shorter key cannot match inside it again.
                    for (int i = at; i < at + key.getKey().length(); i++) {
                        text.setCharAt(i, '#');
                    }
                    at = text.indexOf(key.getKey(), at + key.getKey().length());
                }
            }
            return List.copyOf(found);
        }
    }

    // ------------------------------------------------------------------ the parsed row

    public record ParsedContact(String name, String roleTitle, String phone, String email, boolean decisionMaker) {
    }

    /** One history cell. {@code label} is the column header, {@code text} the cell exactly as typed. */
    public record ParsedHistory(String label, String text, LocalDate date, ActivityType type, ActivityResult result,
            boolean plainComment) {
    }

    public record ParsedRow(int index, String name, String legalName, String address, String city, String district,
            String typeText, String phone, String email, String website, String idCode, Integer branches,
            List<ParsedContact> contacts, UsageAnswer usesSyrup, List<String> brands, List<String> flavors,
            String flavorText, List<ParsedHistory> history, List<String> sampleFlavors, String samplesText,
            boolean customer, boolean refused, String nextStep, LocalDate nextStepDate, BusinessStatus status,
            String error, Map<Long, String> customValues) {
    }

    // ------------------------------------------------------------------ parsing

    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{1,2})[/.,](\\d{1,2})(?:[/.,](\\d{4}|\\d{2}))?(?!\\d)");
    private static final Pattern PHONE = Pattern.compile("\\+?\\(?\\d[\\d\\s()\\-]{6,}\\d");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern DIGITS = Pattern.compile("\\d{9,11}");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private static final String[] ROLES = {"მთავარი ბარ მენეჯერი", "ბარ მენეჯერი", "ბარმენი", "მენეჯერი", "დირექტორი",
            "მფლობელი", "მეპატრონე", "ადმინისტრატორი", "სუპერვაიზორი", "შეფი", "owner/manager", "owner", "manager",
            "director", "bartender", "bar manager"};
    private static final Pattern DECIDES = Pattern.compile("დირექტორ|მფლობელ|მეპატრონ|მთავარ|owner|director", Pattern.CASE_INSENSITIVE);

    private static final Pattern NO_ANSWER = Pattern.compile("არაიღ|არ\\s?იღ|არ\\s?უპასუხ|no answer|არ\\s?პასუხ");
    private static final Pattern REFUSED = Pattern.compile("უარი|არ\\s?გვინდ|არ\\s?მინდ|არ\\s?მომეწონ|არ\\s?მოგვეწონ|არ\\s?მოეწონ|not interested");
    private static final Pattern ORDERED = Pattern.compile("შეუკვეთ|შევუკვეთ|შეკვეთ|ordered");
    private static final Pattern LIKED = Pattern.compile("მოეწონ|მომეწონ|დაინტერეს|interested");
    private static final Pattern CALL_BACK = Pattern.compile("გადმოგირეკ|გადაურეკ|დავურეკ|დარეკეო|call back");

    /** A cell that only says "no" or "we did not have one": not history worth keeping. */
    private static final Pattern EMPTY_ANSWER = Pattern.compile(
            "^(არა|no|-|—|არ გვქონია( შეხვედრა)?|შეხვედრა არ გვქონია|არა,? ჯერ)[.!]?$", Pattern.CASE_INSENSITIVE);

    public static ParsedRow parse(int index, List<String> headers, List<String> cells, Map<Integer, String> mapping,
            Vocabulary vocabulary, LocalDate today) {
        Map<Field, String> single = new LinkedHashMap<>();
        Map<Long, String> customValues = new LinkedHashMap<>();
        List<String[]> history = new ArrayList<>();
        List<String[]> comments = new ArrayList<>();
        List<String[]> samples = new ArrayList<>();

        for (Map.Entry<Integer, String> column : mapping.entrySet()) {
            int i = column.getKey();
            String value = i < cells.size() ? Text.blankToNull(cells.get(i)) : null;
            if (value == null) {
                continue;
            }
            Long customId = customFieldId(column.getValue());
            if (customId != null) {
                customValues.merge(customId, value, (a, b) -> a + "; " + b);
                continue;
            }
            Field field = fieldOf(column.getValue());
            if (field == Field.IGNORE) {
                continue;
            }
            String label = label(i < headers.size() ? headers.get(i) : "");
            switch (field) {
                case HISTORY -> history.add(new String[] {label, value});
                case COMMENT -> comments.add(new String[] {label, value});
                case SAMPLES -> samples.add(new String[] {label, value});
                default -> single.merge(field, value, (a, b) -> a + "; " + b);
            }
        }

        String name = single.get(Field.NAME);
        String usageText = single.get(Field.SYRUP_USAGE);
        String flavorText = single.get(Field.FLAVORS);

        List<String> brands = new ArrayList<>(vocabulary.brandsIn(join(usageText, flavorText)));
        List<String> flavors = flavorText == null ? List.of() : vocabulary.flavorsIn(flavorText);
        UsageAnswer usesSyrup = usageAnswer(usageText, !brands.isEmpty());

        List<ParsedHistory> parsedHistory = new ArrayList<>();
        for (String[] cell : history) {
            if (!EMPTY_ANSWER.matcher(cell[1].trim()).matches()) {
                parsedHistory.add(historyOf(cell[0], cell[1], today, null));
            }
        }
        List<String> sampleFlavors = new ArrayList<>();
        String samplesText = null;
        for (String[] cell : samples) {
            if (EMPTY_ANSWER.matcher(cell[1].trim()).matches() || cell[1].trim().startsWith("არა")) {
                continue;
            }
            samplesText = samplesText == null ? cell[1] : samplesText + "; " + cell[1];
            sampleFlavors.addAll(vocabulary.flavorsIn(cell[1]));
            parsedHistory.add(historyOf(cell[0], cell[1], today, ActivityType.SAMPLES));
        }
        for (String[] cell : comments) {
            ParsedHistory item = historyOf(cell[0], cell[1], today, null);
            // Undated text with no call or visit in it is a remark, not an event.
            boolean plain = item.date() == null && item.type() == ActivityType.OTHER;
            parsedHistory.add(plain ? new ParsedHistory(item.label(), item.text(), null, null, null, true) : item);
        }

        String customerText = single.get(Field.CUSTOMER);
        boolean customer = customerText != null && startsWithYes(customerText);
        if (customerText != null && !EMPTY_ANSWER.matcher(customerText.trim()).matches()) {
            ParsedHistory item = historyOf(label(headerOf(headers, mapping, Field.CUSTOMER)), customerText, today, null);
            parsedHistory.add(customer && item.result() == ActivityResult.TALKED
                    ? new ParsedHistory(item.label(), item.text(), item.date(), item.type(), ActivityResult.ORDERED, false) : item);
        }

        boolean refused = !customer && parsedHistory.stream()
                .anyMatch(h -> REFUSED.matcher(h.text().toLowerCase(Locale.ROOT)).find());
        BusinessStatus status = customer ? BusinessStatus.CUSTOMER
                : refused ? BusinessStatus.NOT_INTERESTED
                : parsedHistory.isEmpty() && samplesText == null ? BusinessStatus.NEW
                : samplesText != null ? BusinessStatus.TESTING
                : BusinessStatus.CONTACTED;

        String nextStep = single.get(Field.NEXT_STEP);
        LocalDate nextDate = nextStep == null ? null : firstDate(nextStep, today);

        return new ParsedRow(index, name, single.get(Field.LEGAL_NAME), single.get(Field.ADDRESS), single.get(Field.CITY),
                single.get(Field.DISTRICT), single.get(Field.TYPE), single.get(Field.PHONE), email(single.get(Field.EMAIL)),
                single.get(Field.WEBSITE), idCode(single.get(Field.ID_CODE)), firstNumber(single.get(Field.BRANCHES)),
                contacts(single.get(Field.CONTACTS)), usesSyrup, brands, flavors, flavorText, parsedHistory,
                List.copyOf(new LinkedHashSet<>(sampleFlavors)), samplesText, customer, refused, nextStep, nextDate, status,
                name == null ? "NO_NAME" : null, customValues);
    }

    static ParsedHistory historyOf(String label, String text, LocalDate today, ActivityType forced) {
        String lower = text.toLowerCase(Locale.ROOT);
        String header = label.toLowerCase(Locale.ROOT);
        ActivityType type = forced != null ? forced
                : has(lower, "შეხვ", "დეგუსტ", "meeting", "tasting") ? ActivityType.MEETING
                : has(lower, "მივუტან", "მივიტან", "წავუღ", "ვიზიტ", "visit") ? ActivityType.VISIT
                : has(lower, "ზარ", "დავრეკ", "დარეკ", "დაურეკ", "დავურეკ", "call") ? ActivityType.CALL
                : has(lower, "მეილ", "ჩავუგდ", "მომწერ", "მესიჯ", "email", "message") ? ActivityType.MESSAGE
                : has(header, "შეხვედრ", "meeting") ? ActivityType.MEETING
                : ActivityType.OTHER;
        ActivityResult result = forced == ActivityType.SAMPLES ? ActivityResult.OTHER
                : NO_ANSWER.matcher(lower).find() ? ActivityResult.NO_ANSWER
                : REFUSED.matcher(lower).find() ? ActivityResult.NOT_INTERESTED
                : ORDERED.matcher(lower).find() ? ActivityResult.ORDERED
                : LIKED.matcher(lower).find() ? ActivityResult.INTERESTED
                : CALL_BACK.matcher(lower).find() ? ActivityResult.CALL_BACK
                : ActivityResult.TALKED;
        return new ParsedHistory(label, text, firstDate(text, today), type, result, false);
    }

    /**
     * The first date written in the text. Georgian order (day/month) unless that cannot be a date
     * and month/day can ("7/27/2026"). No year means this year, or last year if that would put it
     * more than a month in the future.
     */
    static LocalDate firstDate(String text, LocalDate today) {
        Matcher m = DATE.matcher(text);
        while (m.find()) {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            int day = a;
            int month = b;
            if (b > 12 && a <= 12) {
                day = b;
                month = a;
            }
            try {
                if (m.group(3) != null) {
                    int year = Integer.parseInt(m.group(3));
                    return LocalDate.of(year < 100 ? 2000 + year : year, month, day);
                }
                LocalDate date = LocalDate.of(today.getYear(), month, day);
                return date.isAfter(today.plusDays(31)) ? date.minusYears(1) : date;
            } catch (DateTimeException ex) {
                // "20.5" of something, not a date; keep looking.
            }
        }
        return null;
    }

    static UsageAnswer usageAnswer(String text, boolean brandsFound) {
        if (text == null) {
            return brandsFound ? UsageAnswer.YES : UsageAnswer.UNKNOWN;
        }
        String lower = text.trim().toLowerCase(Locale.ROOT);
        if (has(lower, "სავარაუდოდ", "ალბათ", "probably", "maybe")) {
            return UsageAnswer.UNKNOWN;
        }
        if (startsWithYes(lower)) {
            return UsageAnswer.YES;
        }
        if (lower.startsWith("არა") || lower.startsWith("no")) {
            return UsageAnswer.NO;
        }
        return brandsFound ? UsageAnswer.YES : UsageAnswer.UNKNOWN;
    }

    /**
     * "ნინი მენეჯერი(599 002 677),  ბარმენი იოსები, 599 244 922 სერგი (მთავარი ბარ მენეჯერი)" becomes
     * three people. A piece that is only a phone number goes to the person before it.
     */
    static List<ParsedContact> contacts(String text) {
        if (text == null) {
            return List.of();
        }
        List<ParsedContact> people = new ArrayList<>();
        for (String piece : text.split("[,;\\n]")) {
            String rest = piece.trim();
            if (rest.isEmpty()) {
                continue;
            }
            String phone = null;
            Matcher phoneMatch = PHONE.matcher(rest);
            while (phoneMatch.find()) {
                String candidate = phoneMatch.group().trim();
                // "მენეჯერი(599 002 677)": the opening bracket belongs to the note, not the number.
                if (candidate.startsWith("(") && !candidate.contains(")")) {
                    candidate = candidate.substring(1);
                }
                if (candidate.replaceAll("\\D", "").length() >= 7) {
                    phone = phone == null ? candidate : phone + " / " + candidate;
                    rest = rest.replace(phoneMatch.group(), " ");
                }
            }
            String email = null;
            Matcher emailMatch = EMAIL.matcher(rest);
            if (emailMatch.find()) {
                email = emailMatch.group();
                rest = rest.replace(email, " ");
            }
            String role = null;
            Matcher brackets = Pattern.compile("\\(([^)]*)\\)").matcher(rest);
            if (brackets.find() && Text.blankToNull(brackets.group(1)) != null) {
                role = brackets.group(1).trim();
                rest = rest.replace(brackets.group(), " ");
            }
            if (role == null) {
                String lower = rest.toLowerCase(Locale.ROOT);
                for (String known : ROLES) {
                    int at = lower.indexOf(known);
                    if (at >= 0) {
                        role = rest.substring(at, at + known.length());
                        rest = rest.substring(0, at) + " " + rest.substring(at + known.length());
                        break;
                    }
                }
            }
            String name = rest.replaceAll("[()\\[\\]/:\\-]+", " ").replaceAll("\\s+", " ").trim();
            if (name.isEmpty() && role == null && email == null) {
                if (phone != null && !people.isEmpty() && people.get(people.size() - 1).phone() == null) {
                    ParsedContact last = people.remove(people.size() - 1);
                    people.add(new ParsedContact(last.name(), last.roleTitle(), phone, last.email(), last.decisionMaker()));
                    continue;
                }
                if (phone == null) {
                    continue;
                }
            }
            if (name.isEmpty()) {
                name = role != null ? role : phone != null ? phone : email;
            }
            boolean decides = role != null && DECIDES.matcher(role).find();
            people.add(new ParsedContact(truncate(name, 120), role == null ? null : truncate(role, 80),
                    phone == null ? null : truncate(phone, 60), email, decides));
        }
        return people;
    }

    static String idCode(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = DIGITS.matcher(text.replaceAll("[\\s-]", ""));
        return m.find() ? m.group() : truncate(text.trim(), 40);
    }

    private static String email(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = EMAIL.matcher(text);
        return m.find() ? truncate(m.group(), 120) : null;
    }

    private static Integer firstNumber(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.parseInt(m.group());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean startsWithYes(String text) {
        String lower = text.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("კი") || lower.startsWith("yes") || lower.startsWith("დიახ");
    }

    private static String headerOf(List<String> headers, Map<Integer, String> mapping, Field field) {
        return mapping.entrySet().stream()
                .filter(e -> field.name().equals(e.getValue()) && e.getKey() < headers.size())
                .map(e -> headers.get(e.getKey()))
                .findFirst().orElse("");
    }

    /** A header's first line, short enough to prefix a note with: "I დაკავშირების თარიღი". */
    static String label(String header) {
        String firstLine = header == null ? "" : header.split("\\R")[0].replaceAll("[;:]+$", "").trim();
        return truncate(firstLine, 40);
    }

    private static String join(String a, String b) {
        return (a == null ? "" : a) + " " + (b == null ? "" : b);
    }

    private static boolean has(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    static String squash(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[\\s\\-_.]+", "");
    }

    static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
