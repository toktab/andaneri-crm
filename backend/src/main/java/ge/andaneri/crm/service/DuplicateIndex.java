package ge.andaneri.crm.service;

import ge.andaneri.crm.common.Text;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.web.BusinessDtos.DuplicateDto;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds businesses that are probably the same place, by identification code, phone and name.
 *
 * <p>A match is <b>strong</b> (certainly the same place) when the names are alike and the addresses
 * do not contradict each other. A shared phone or ID code alone is only a <b>possible</b> duplicate:
 * the team's own spreadsheet has chain branches on one central number ("Chaduna (Tabidze)" and
 * "Chaduna (Plexanovi)") and sister venues of one company under one ID code. An import skips only
 * strong matches, so a branch is never silently lost. A shared name at two different addresses is
 * a branch and not reported at all.
 *
 * <p>Built once per import, so thousands of rows are checked without a query each.
 */
public final class DuplicateIndex {

    private record Entry(Long id, String name, String address, String phone) {
    }

    /**
     * Words that say what kind of place it is rather than which place: venue types, cuisines, and the
     * like. "Lumos Thai Restaurant" and "Yummy Thai Restaurant" share only these.
     */
    private static final Set<String> GENERIC = Set.of(
            "cafe", "coffee", "coffeeshop", "restaurant", "resto", "shop", "bakery", "pastry", "wine", "winebar",
            "house", "kitchen", "food", "tbilisi", "georgia", "club", "hotel", "garden", "street", "lounge", "tasting",
            "corner", "craft", "gastro", "station", "express", "bistro", "grill", "pizza", "pizzeria", "burger",
            "sushi", "thai", "asian", "italian", "georgian", "chinese", "japanese", "korean", "indian", "mexican",
            "ukrainian", "french", "cuisine", "cocktail", "cocktails", "speakeasy", "tavern", "diner", "tea",
            "კაფე", "ყავა", "ყავის", "ბარი", "რესტორანი", "პაბი", "ლაუნჯი", "საცხობი", "სასტუმრო", "თბილისი",
            "სუში", "პიცა", "სამზარეულო", "ღვინის", "ღვინო");

    private final Map<String, List<Entry>> byIdCode = new HashMap<>();
    private final Map<String, List<Entry>> byPhone = new HashMap<>();
    private final Map<String, List<Entry>> byName = new HashMap<>();

    public static DuplicateIndex of(List<Business> businesses) {
        DuplicateIndex index = new DuplicateIndex();
        for (Business b : businesses) {
            index.add(b.getId(), b.getName(), b.getAddress(), b.getPhone(), b.getIdCode());
        }
        return index;
    }

    /** Adds a row, so later rows of the same import are checked against it too ({@code id} null for those). */
    public void add(Long id, String name, String address, String phone, String idCode) {
        Entry entry = new Entry(id, name, address, phone);
        String code = Text.blankToNull(idCode);
        if (code != null) {
            byIdCode.computeIfAbsent(code.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(entry);
        }
        String phoneKey = Text.phoneKey(phone);
        if (phoneKey.length() >= 7) {
            byPhone.computeIfAbsent(phoneKey, key -> new ArrayList<>()).add(entry);
        }
        String nameKey = Text.normalize(name);
        if (!nameKey.isEmpty()) {
            byName.computeIfAbsent(nameKey, key -> new ArrayList<>()).add(entry);
        }
    }

    /** The most certain match, if any. */
    public Optional<DuplicateDto> match(String name, String address, String phone, String idCode, Long excludeId) {
        List<DuplicateDto> all = matches(name, address, phone, idCode, excludeId);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /** Up to five matches, strong ones first. */
    public List<DuplicateDto> matches(String name, String address, String phone, String idCode, Long excludeId) {
        Map<Entry, DuplicateDto> found = new LinkedHashMap<>();
        String code = Text.blankToNull(idCode);
        if (code != null) {
            for (Entry entry : byIdCode.getOrDefault(code.toLowerCase(Locale.ROOT), List.of())) {
                put(found, entry, "ID_CODE", similarNames(name, entry.name()) && compatibleAddresses(address, entry.address()), excludeId);
            }
        }
        String phoneKey = Text.phoneKey(phone);
        if (phoneKey.length() >= 7) {
            for (Entry entry : byPhone.getOrDefault(phoneKey, List.of())) {
                put(found, entry, "PHONE", similarNames(name, entry.name()) && compatibleAddresses(address, entry.address()), excludeId);
            }
        }
        for (Entry entry : byName.getOrDefault(Text.normalize(name), List.of())) {
            if (compatibleAddresses(address, entry.address())) {
                put(found, entry, "NAME", true, excludeId);
            }
        }
        return found.values().stream()
                .sorted(Comparator.comparing((DuplicateDto d) -> !d.strong()))
                .limit(5)
                .toList();
    }

    private static void put(Map<Entry, DuplicateDto> found, Entry entry, String reason, boolean strong, Long excludeId) {
        if (excludeId != null && excludeId.equals(entry.id())) {
            return;
        }
        DuplicateDto existing = found.get(entry);
        if (existing == null || (strong && !existing.strong())) {
            found.put(entry, new DuplicateDto(entry.id(), entry.name(), entry.address(), entry.phone(), reason, strong));
        }
    }

    /** Same name once punctuation, accents and case are gone, or a distinctive word in common ("Kombinati" and "CAFE KOMBINATI"). */
    static boolean similarNames(String a, String b) {
        String na = Text.normalize(a);
        if (!na.isEmpty() && na.equals(Text.normalize(b))) {
            return true;
        }
        Set<String> tokens = tokens(a);
        return tokens(b).stream().anyMatch(tokens::contains);
    }

    /**
     * Addresses agree unless both are known and describe different places. "Paliashvili, 24" and
     * "24 Paliashvili St." agree: the same house number and a street word in common.
     */
    static boolean compatibleAddresses(String a, String b) {
        String na = Text.normalize(a);
        String nb = Text.normalize(b);
        if (na.isEmpty() || nb.isEmpty() || na.contains(nb) || nb.contains(na)) {
            return true;
        }
        Set<String> numbersA = numbers(a);
        boolean sameNumber = numbers(b).stream().anyMatch(numbersA::contains);
        Set<String> wordsA = streetWords(a);
        return sameNumber && streetWords(b).stream().anyMatch(wordsA::contains);
    }

    private static final Set<String> STREET_WORDS = Set.of("street", "avenue", "building", "floor", "ქუჩა", "პროსპექტი", "კორპუსი");

    private static Set<String> numbers(String value) {
        return Arrays.stream(value.split("\\D+")).filter(n -> !n.isEmpty()).collect(Collectors.toSet());
    }

    private static Set<String> streetWords(String value) {
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(plain.split("[^\\p{L}]+"))
                .filter(word -> word.length() >= 4 && !STREET_WORDS.contains(word))
                .collect(Collectors.toSet());
    }

    private static Set<String> tokens(String value) {
        if (value == null) {
            return Set.of();
        }
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(plain.split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() >= 4 && !GENERIC.contains(token))
                .collect(Collectors.toSet());
    }
}
