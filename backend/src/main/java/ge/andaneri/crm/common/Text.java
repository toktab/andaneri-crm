package ge.andaneri.crm.common;

import java.text.Normalizer;
import java.util.Locale;

/** Small string helpers shared by the services, the import and the duplicate check. */
public final class Text {

    private Text() {
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * A name or address reduced to letters and digits, lower case, so "Café Tiflis, LLC" and
     * "cafe tiflis llc" compare equal. Georgian letters are kept as they are (they have no case).
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String cleaned = decomposed.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        // Company-form words that differ between spreadsheets and the Maps listing.
        return cleaned.replaceAll("(llc|ltd|შპს)", "");
    }

    /**
     * The last nine digits of a phone number: a Georgian mobile without the +995 prefix, so
     * "+995 555 12 34 56", "555123456" and "0555-12-34-56" are the same number.
     */
    public static String phoneKey(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.length() > 9 ? digits.substring(digits.length() - 9) : digits;
    }
}
