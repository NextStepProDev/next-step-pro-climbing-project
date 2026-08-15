package pl.nextsteppro.climbing.domain.climbingascent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AscentTextKeyTest {

    @Test
    @DisplayName("the same crag typed three ways collapses to one key")
    void shouldCollapseSpellingVariantsOfTheSamePlace() {
        String expected = AscentTextKey.normalize("Jura Północna");

        assertThat(AscentTextKey.normalize("jura polnocna")).isEqualTo(expected);
        assertThat(AscentTextKey.normalize("  JURA   PÓŁNOCNA  ")).isEqualTo(expected);
        assertThat(AscentTextKey.normalize("Jura PÓŁNOCNA")).isEqualTo(expected);
    }

    @Test
    @DisplayName("ł has no combining mark in NFD, so it needs its own rule")
    void shouldFoldTheStrokedL() {
        assertThat(AscentTextKey.normalize("Kołoczek")).isEqualTo("koloczek");
        assertThat(AscentTextKey.normalize("ŁUTNIA")).isEqualTo("lutnia");
    }

    @Test
    void shouldStripEveryPolishDiacritic() {
        assertThat(AscentTextKey.normalize("ĄĆĘŁŃÓŚŹŻ")).isEqualTo("acelnoszz");
    }

    @Test
    void shouldSquashRunsOfWhitespaceToASingleSpace() {
        assertThat(AscentTextKey.normalize("Dolina\t Bolechowicka\n\nGóra"))
                .isEqualTo("dolina bolechowicka gora");
    }

    @Test
    void shouldKeepDistinctPlacesDistinct() {
        assertThat(AscentTextKey.normalize("Jura"))
                .isNotEqualTo(AscentTextKey.normalize("Jura Północna"));
    }

    @Test
    void shouldReturnEmptyStringForBlankInput() {
        assertThat(AscentTextKey.normalize("   ")).isEmpty();
        assertThat(AscentTextKey.normalize("")).isEmpty();
    }

    @Test
    void shouldLeaveNonPolishLatinNamesUsable() {
        assertThat(AscentTextKey.normalize("Céüse")).isEqualTo("ceuse");
        assertThat(AscentTextKey.normalize("Siurana")).isEqualTo("siurana");
    }
}
