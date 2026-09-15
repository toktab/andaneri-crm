package ge.andaneri.crm.io;

import static org.assertj.core.api.Assertions.assertThat;

import ge.andaneri.crm.domain.ActivityResult;
import ge.andaneri.crm.domain.ActivityType;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.UsageAnswer;
import ge.andaneri.crm.io.ImportParser.Field;
import ge.andaneri.crm.io.ImportParser.ParsedContact;
import ge.andaneri.crm.io.ImportParser.ParsedHistory;
import ge.andaneri.crm.io.ImportParser.ParsedRow;
import ge.andaneri.crm.io.ImportParser.Vocabulary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Cells and headers shaped like the team's "Sales Report Form" spreadsheet. */
class ImportParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

    /** The 19 headers of the real sheet, including the line breaks some of them contain. */
    static final List<String> HEADERS = List.of(
            "სავაჭრო დასახელება", "მისამართი", "ტელეფონი", "ელ.ფოსტა", "ვებ გვერდი", "ს.კ.", "შპს დასახელება",
            "დირექტორი/მენეჯერი სახელი, ტელ. მეილ", "ფილიალების რ-ბა",
            "მოიხმარენ თუ არა სიროფს კი/არა; თუ კი, რომელი ბრენდის", "რა სახეობებს და თვეში რამდენს",
            "I დაკავშირების თარიღი\nშინაარსი;\nდაგეგმილი", "შეხვედრის შინაარსი",
            "გაიგზავნა თუ არა ნიმუშები და რა გაიგზავნა", "II დაკავშირების თარიღი და უკუკავშირი",
            "გახდა თუ არა კლიენტი და როდის", "თავისუფალი კომენტარი", "შემდეგი ნაბიჯი", "პასუხი");

    static final Vocabulary VOCABULARY = new Vocabulary(List.of(
            new String[] {"ვანილი", "Vanilla"}, new String[] {"კარამელი", "Caramel"},
            new String[] {"მარილიანი კარამელი", "Salted Caramel"}, new String[] {"შოკოლადი", "Chocolate"},
            new String[] {"ფისტა", "Pistachio"}, new String[] {"ნუში", "Almond"}, new String[] {"ტყის კენკრა", "Wild Berries"},
            new String[] {"მოხიტო", "Mojito"}, new String[] {"პიტნა", "Mint"}, new String[] {"ლიმონი", "Lemon"},
            new String[] {"მარაკუია", "Passion Fruit"}, new String[] {"ატამი", "Peach"}, new String[] {"მანგო", "Mango"},
            new String[] {"გრენადინი", "Grenadine"}, new String[] {"თაფლი", "Honey"}, new String[] {"ტირამისუ", "Tiramisu"},
            new String[] {"ბლუ კურასაო", "Blue Curacao"}, new String[] {"ქოქოსი", "Coconut"}, new String[] {"ლავანდა", "Lavender"}),
            List.of("Andaneri", "Monin", "1883 Maison Routin", "Black Sea"));

    @Test
    void recognisesEveryColumnOfTheSalesReportForm() {
        Map<Integer, String> mapping = ImportParser.guessMapping(HEADERS);
        assertThat(mapping.values().stream().map(Field::valueOf).toList()).containsExactly(
                Field.NAME, Field.ADDRESS, Field.PHONE, Field.EMAIL, Field.WEBSITE, Field.ID_CODE, Field.LEGAL_NAME,
                Field.CONTACTS, Field.BRANCHES, Field.SYRUP_USAGE, Field.FLAVORS, Field.HISTORY, Field.HISTORY,
                Field.SAMPLES, Field.HISTORY, Field.CUSTOMER, Field.COMMENT, Field.NEXT_STEP, Field.HISTORY);
    }

    @Test
    void splitsAContactsCellIntoPeopleWithRolesAndPhones() {
        List<ParsedContact> people = ImportParser.contacts(
                "ნინი მენეჯერი(599 002 677),  ბარმენი იოსები,           599 244 922 სერგი (მთავარი ბარ მენეჯერი)");
        assertThat(people).extracting(ParsedContact::name).containsExactly("ნინი", "იოსები", "სერგი");
        assertThat(people).extracting(ParsedContact::roleTitle).containsExactly("მენეჯერი", "ბარმენი", "მთავარი ბარ მენეჯერი");
        assertThat(people).extracting(ParsedContact::phone).containsExactly("599 002 677", null, "599 244 922");
        assertThat(people).extracting(ParsedContact::decisionMaker).containsExactly(false, false, true);
    }

    @Test
    void aDirectorDecides() {
        List<ParsedContact> people = ImportParser.contacts("სანდრო (ბარმენი), იაგო (დირექტორი ) 577 56 00 11");
        assertThat(people).hasSize(2);
        assertThat(people.get(1).name()).isEqualTo("იაგო");
        assertThat(people.get(1).decisionMaker()).isTrue();
        assertThat(people.get(1).phone()).isEqualTo("577 56 00 11");
    }

    @Test
    void findsFlavorsInDeclinedGeorgianWords() {
        assertThat(VOCABULARY.flavorsIn("გრენადინი, თაფლი, პიტნის, კენკრას, ვანილი, ნუში."))
                .containsExactlyInAnyOrder("Grenadine", "Honey", "Mint", "Wild Berries", "Vanilla", "Almond");
    }

    @Test
    void theLongerFlavorWins() {
        assertThat(VOCABULARY.flavorsIn("პაშენ ფრუტი, ატამი, კენკრა. მარილიანი კარამელი, ფისტა"))
                .containsExactlyInAnyOrder("Passion Fruit", "Peach", "Wild Berries", "Salted Caramel", "Pistachio");
    }

    @Test
    void lemonadeIsNotLemon() {
        assertThat(VOCABULARY.flavorsIn("ლიმონათები მინდაო, გრენადინი")).containsExactly("Grenadine");
    }

    @Test
    void findsBrandsByTheirLocalSpelling() {
        assertThat(VOCABULARY.brandsIn("კი; 1883,მონინს")).containsExactlyInAnyOrder("1883 Maison Routin", "Monin");
    }

    @Test
    void readsDatesTheWayTheyAreTyped() {
        assertThat(ImportParser.firstDate("30/07/2026 – შეხვ.+დეგ.", TODAY)).isEqualTo(LocalDate.of(2026, 7, 30));
        assertThat(ImportParser.firstDate("24,08,2025", TODAY)).isEqualTo(LocalDate.of(2025, 8, 24));
        assertThat(ImportParser.firstDate("1 კვირაში დარეკეო, ეხა არის 7/27/2026თი", TODAY)).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(ImportParser.firstDate("(26/8/2026)-ში დავრეკე", TODAY)).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(ImportParser.firstDate("დავრეკე 8/9/26-ში", TODAY)).isEqualTo(LocalDate.of(2026, 9, 8));
        // No year: this year, unless that is well into the future.
        assertThat(ImportParser.firstDate("2/9-ში დავრეკე", TODAY)).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(ImportParser.firstDate("20/12 შეხვედრა", TODAY)).isEqualTo(LocalDate.of(2025, 12, 20));
        assertThat(ImportParser.firstDate("3-4 სკენ პლანტასტიკ", TODAY)).isNull();
    }

    @Test
    void guessesWhatKindOfContactACellDescribes() {
        ParsedHistory noAnswer = ImportParser.historyOf("კომენტარი", "2/9-ში-ში დავრეკე და არაიღეს", TODAY, null);
        assertThat(noAnswer.type()).isEqualTo(ActivityType.CALL);
        assertThat(noAnswer.result()).isEqualTo(ActivityResult.NO_ANSWER);

        ParsedHistory refused = ImportParser.historyOf("პასუხი", "10/9/2026-ში დავრეკე და არმომეწონაო, არავისო,", TODAY, null);
        assertThat(refused.result()).isEqualTo(ActivityResult.NOT_INTERESTED);
        assertThat(refused.date()).isEqualTo(LocalDate.of(2026, 9, 10));

        ParsedHistory ordered = ImportParser.historyOf("პასუხი", "დავრეკე 8/9/26-ში და  შევუკვეთავთო მოვაწერინე", TODAY, null);
        assertThat(ordered.result()).isEqualTo(ActivityResult.ORDERED);

        ParsedHistory tasting = ImportParser.historyOf("შეხვედრის შინაარსი", "დეგუსტაცია, შევადარეთ ჩვენი სიროფები", TODAY, null);
        assertThat(tasting.type()).isEqualTo(ActivityType.MEETING);
    }

    @Test
    void answersTheSyrupQuestion() {
        assertThat(ImportParser.usageAnswer("კი; 1883,მონინს", true)).isEqualTo(UsageAnswer.YES);
        assertThat(ImportParser.usageAnswer("არა, მხოლოდ ღვინოებს.", false)).isEqualTo(UsageAnswer.NO);
        assertThat(ImportParser.usageAnswer("სავარაუდოდ მოიხმარენ რადგან iced matcha", false)).isEqualTo(UsageAnswer.UNKNOWN);
    }

    @Test
    void cleansIdentificationCodes() {
        assertThat(ImportParser.idCode("ს/კ 400382581")).isEqualTo("400382581");
        assertThat(ImportParser.idCode("iD *405432045")).isEqualTo("405432045");
        assertThat(ImportParser.idCode("60001042440")).isEqualTo("60001042440");
    }

    @Test
    void aWholeRowKeepsEveryHistoryCellAndLayersGuessesOnTop() {
        List<String> cells = row(
                "meama collect", "Apakidze, 1", "", "info@meamacollect.ge", "https://meamacollect.ge/", "406324259",
                "შპს მეამა ქოლექთი", "დავით ბოკუჩვა", "27", "კი; 1883,მონინს",
                "ფისტა, ვანილი, შოკოლადი, ტირამისუ, მოხიტო", "24,08,2025 ჩავუგდეთ პრაისები", "არ გვქონია შეხვედრა",
                "კი – მარწყვი, ჟოლო, ტროპიკი (დეგუსტაცია)", "30/07 ზარი – პ.არ იყო.", "კი – შეუკვეთა 12 ბოთლი",
                "უნდა დავრეკოთ", "", "");
        ParsedRow parsed = ImportParser.parse(0, HEADERS, cells, ImportParser.guessMapping(HEADERS), VOCABULARY, TODAY);

        assertThat(parsed.error()).isNull();
        assertThat(parsed.name()).isEqualTo("meama collect");
        assertThat(parsed.legalName()).isEqualTo("შპს მეამა ქოლექთი");
        assertThat(parsed.idCode()).isEqualTo("406324259");
        assertThat(parsed.branches()).isEqualTo(27);
        assertThat(parsed.usesSyrup()).isEqualTo(UsageAnswer.YES);
        assertThat(parsed.brands()).containsExactlyInAnyOrder("1883 Maison Routin", "Monin");
        assertThat(parsed.flavors()).containsExactlyInAnyOrder("Pistachio", "Vanilla", "Chocolate", "Tiramisu", "Mojito");
        assertThat(parsed.contacts()).extracting(ParsedContact::name).containsExactly("დავით ბოკუჩვა");
        assertThat(parsed.customer()).isTrue();
        assertThat(parsed.status()).isEqualTo(BusinessStatus.CUSTOMER);

        // "We did not have a meeting" is dropped; everything else is kept exactly as typed.
        assertThat(parsed.history()).extracting(ParsedHistory::text).containsExactlyInAnyOrder(
                "24,08,2025 ჩავუგდეთ პრაისები", "30/07 ზარი – პ.არ იყო.", "კი – მარწყვი, ჟოლო, ტროპიკი (დეგუსტაცია)",
                "უნდა დავრეკოთ", "კი – შეუკვეთა 12 ბოთლი");
        ParsedHistory prices = parsed.history().stream().filter(h -> h.text().startsWith("24,08")).findFirst().orElseThrow();
        assertThat(prices.type()).isEqualTo(ActivityType.MESSAGE);
        assertThat(prices.date()).isEqualTo(LocalDate.of(2025, 8, 24));
        ParsedHistory remark = parsed.history().stream().filter(h -> h.text().equals("უნდა დავრეკოთ")).findFirst().orElseThrow();
        assertThat(remark.type()).isEqualTo(ActivityType.CALL);
    }

    @Test
    void anOutrightNoMakesItNotInterested() {
        List<String> cells = row("წიბახა", "", "557036969", "", "", "", "", "", "", "კი, მონინი და 1883", "ნუში გრენადინი ბლუკურასაო",
                "", "", "", "", "", "2/9-ში დავრეკე, სანდრო დამალაპარაკეს", "", "10/9/2026-ში დავრეკე და არმომეწონაო");
        ParsedRow parsed = ImportParser.parse(1, HEADERS, cells, ImportParser.guessMapping(HEADERS), VOCABULARY, TODAY);
        assertThat(parsed.flavors()).containsExactlyInAnyOrder("Almond", "Grenadine", "Blue Curacao");
        assertThat(parsed.refused()).isTrue();
        assertThat(parsed.status()).isEqualTo(BusinessStatus.NOT_INTERESTED);
    }

    @Test
    void aColumnMappedToACustomFieldKeepsItsText() {
        Map<Integer, String> mapping = new java.util.LinkedHashMap<>(ImportParser.guessMapping(HEADERS));
        mapping.put(8, "CUSTOM:7");
        mapping.put(2, "CUSTOM:7");
        ParsedRow parsed = ImportParser.parse(0, HEADERS, row("Blanco", "", "599 00 00 00", "", "", "", "", "", "3"), mapping, VOCABULARY, TODAY);
        assertThat(parsed.customValues()).containsEntry(7L, "599 00 00 00; 3");
        assertThat(parsed.branches()).isNull();
        assertThat(parsed.phone()).isNull();
    }

    @Test
    void aRowWithoutANameIsRejected() {
        ParsedRow parsed = ImportParser.parse(2, HEADERS, row("", "Paliashvili, 24"), ImportParser.guessMapping(HEADERS), VOCABULARY, TODAY);
        assertThat(parsed.error()).isEqualTo("NO_NAME");
    }

    private static List<String> row(String... values) {
        List<String> cells = new ArrayList<>(List.of(values));
        while (cells.size() < HEADERS.size()) {
            cells.add("");
        }
        return cells;
    }
}
