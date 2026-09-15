package ge.andaneri.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import ge.andaneri.crm.web.BusinessDtos.DuplicateDto;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pairs taken from the team's spreadsheet, where the first version of this check skipped real branches. */
class DuplicateIndexTest {

    @Test
    void theSamePlaceWrittenTwiceIsCertain() {
        DuplicateIndex index = new DuplicateIndex();
        index.add(1L, "Fika- swedish café", null, "595 12 34 56", null);
        Optional<DuplicateDto> match = index.match("FIKA Swedish Cafe", null, "+995595123456", null, null);
        assertThat(match).get().extracting(DuplicateDto::strong).isEqualTo(true);
    }

    @Test
    void aDistinctiveWordInCommonIsEnough() {
        DuplicateIndex index = new DuplicateIndex();
        index.add(1L, "CAFE KOMBINATI", "Tsereteli 14", null, "405111222");
        assertThat(index.match("Kombinati *კომბინატი", null, null, "405111222", null)).get().extracting(DuplicateDto::strong).isEqualTo(true);
    }

    @Test
    void branchesOnOneCentralNumberAreOnlyPossible() {
        DuplicateIndex index = new DuplicateIndex();
        index.add(1L, "Chaduna (Plexanovi)", "Plekhanov 20", "555 00 11 22", null);
        DuplicateDto match = index.match("Chaduna (Tabidze)", "Tabidze 3", "555001122", null, null).orElseThrow();
        assertThat(match.reason()).isEqualTo("PHONE");
        assertThat(match.strong()).isFalse();
    }

    @Test
    void sisterVenuesOfOneCompanyAreOnlyPossible() {
        DuplicateIndex index = new DuplicateIndex();
        index.add(1L, "Weller", "Abashidze 5", null, "404123456");
        DuplicateDto match = index.match("Craft Wine Restaurant", "Rustaveli 1", null, "404123456", null).orElseThrow();
        assertThat(match.strong()).isFalse();
    }

    @Test
    void genericWordsDoNotMakeNamesAlike() {
        DuplicateIndex index = new DuplicateIndex();
        index.add(1L, "Lumos Thai Restaurant", null, "577 11 22 33", null);
        assertThat(index.match("Yummy Thai Restaurant", null, "577112233", null, null)).get().extracting(DuplicateDto::strong).isEqualTo(false);
    }

    @Test
    void theSameNameAtAnotherAddressIsABranchNotADuplicate() {
        DuplicateIndex index = new DuplicateIndex();
        index.add(1L, "Coffeesta", "Vazha-Pshavela 1", null, null);
        assertThat(index.matches("Coffeesta", "Chavchavadze 2", null, null, null)).isEmpty();
    }

    @Test
    void theSameAddressWrittenDifferentlyStillAgrees() {
        DuplicateIndex index = new DuplicateIndex();
        index.add(1L, "Luis coffee", "Paliashvili, 24", "599 12 12 12", null);
        assertThat(index.match("Luis Coffee", "24 Paliashvili St.", "599121212", null, null)).get().extracting(DuplicateDto::strong).isEqualTo(true);
        // Another house on the same street is another place.
        assertThat(DuplicateIndex.compatibleAddresses("Paliashvili, 24", "Paliashvili, 40")).isFalse();
    }

    @Test
    void aBusinessIsNotItsOwnDuplicate() {
        DuplicateIndex index = new DuplicateIndex();
        index.add(7L, "Blanco", null, "599 00 00 00", null);
        assertThat(index.matches("Blanco", null, "599000000", null, 7L)).isEmpty();
    }
}
