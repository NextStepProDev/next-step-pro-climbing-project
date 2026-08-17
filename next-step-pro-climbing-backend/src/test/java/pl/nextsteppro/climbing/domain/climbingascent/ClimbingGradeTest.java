package pl.nextsteppro.climbing.domain.climbingascent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClimbingGradeTest {

    @ParameterizedTest
    @EnumSource(GradeScale.class)
    void shouldOrderGradesStrictlyAscendingWithinEachScale(GradeScale scale) {
        List<ClimbingGrade> grades = ClimbingGrade.of(scale);

        assertThat(grades).isNotEmpty();
        for (int i = 1; i < grades.size(); i++) {
            assertThat(grades.get(i).rank())
                    .as("%s must be harder than %s", grades.get(i), grades.get(i - 1))
                    .isGreaterThan(grades.get(i - 1).rank());
        }
    }

    @ParameterizedTest
    @EnumSource(GradeScale.class)
    void shouldNotRepeatALabelWithinAScale(GradeScale scale) {
        List<String> labels = ClimbingGrade.of(scale).stream().map(ClimbingGrade::label).toList();

        assertThat(labels).doesNotHaveDuplicates();
    }

    /**
     * Guards {@code chk_climbing_ascents_grade_scale} (V82): the constraint reads the FR_/FB_
     * prefix off the stored constant name, so a constant whose prefix disagrees with its scale
     * would be rejected by the database with no hint as to why.
     */
    @Test
    @DisplayName("every constant name carries the prefix its scale declares (mirrors the V82 CHECK)")
    void shouldNameEveryConstantWithThePrefixOfItsScale() {
        for (ClimbingGrade grade : ClimbingGrade.values()) {
            assertThat(grade.name())
                    .as("%s is on %s", grade, grade.scale())
                    .startsWith(grade.scale().constantPrefix());
        }
    }

    @Test
    void shouldNotLetTheTwoScalesShareAConstantPrefix() {
        Set<String> prefixes = Set.of(GradeScale.FRENCH_ROUTE.constantPrefix(),
                GradeScale.FONT_BOULDER.constantPrefix());

        assertThat(prefixes).hasSize(GradeScale.values().length);
    }

    @Test
    void shouldCoverEveryGradeExactlyOnceAcrossTheScales() {
        int listed = ClimbingGrade.of(GradeScale.FRENCH_ROUTE).size()
                + ClimbingGrade.of(GradeScale.FONT_BOULDER).size();

        assertThat(listed).isEqualTo(ClimbingGrade.values().length);
    }

    @Test
    void shouldReturnTheHardestGradeWhenAllShareAScale() {
        ClimbingGrade hardest = ClimbingGrade.hardest(
                List.of(ClimbingGrade.FR_6A, ClimbingGrade.FR_7B_PLUS, ClimbingGrade.FR_6C));

        assertThat(hardest).isEqualTo(ClimbingGrade.FR_7B_PLUS);
    }

    @Test
    void shouldReturnNullWhenThereAreNoGrades() {
        assertThat(ClimbingGrade.hardest(List.of())).isNull();
    }

    @Test
    @DisplayName("refuses to compare across scales rather than answering with the bigger rank")
    void shouldThrowWhenGradesSpanTwoScales() {
        assertThatThrownBy(() -> ClimbingGrade.hardest(
                List.of(ClimbingGrade.FR_7A, ClimbingGrade.FB_6A)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("across scales");
    }

    @Test
    void shouldGiveEachDisciplineGradesFromItsOwnScaleOnly() {
        assertThat(AscentDiscipline.SPORT.allows(ClimbingGrade.FR_7A)).isTrue();
        assertThat(AscentDiscipline.TRAD.allows(ClimbingGrade.FR_7A)).isTrue();
        assertThat(AscentDiscipline.BOULDER.allows(ClimbingGrade.FR_7A)).isFalse();
        assertThat(AscentDiscipline.BOULDER.allows(ClimbingGrade.FB_7A)).isTrue();
        assertThat(AscentDiscipline.SPORT.allows(ClimbingGrade.FB_7A)).isFalse();
    }

    /** Mirrors {@code chk_climbing_ascents_boulder_style}: nothing to hang a rope on, and
     *  "unroped" is the normal case, so free solo would label the default. */
    @Test
    void shouldOfferOnlyRopelessStylesForBouldering() {
        assertThat(AscentDiscipline.BOULDER.allowedStyles())
                .containsExactlyInAnyOrder(AscentStyle.OS, AscentStyle.FLASH, AscentStyle.RP);
    }

    /** Mirrors {@code chk_climbing_ascents_mountain_style}: no rope to hang down an alpine route. */
    @Test
    void shouldNotOfferTopropeInTheMountains() {
        assertThat(AscentDiscipline.MOUNTAIN_STYLES).doesNotContain(AscentStyle.TR);
        assertThat(AscentDiscipline.MOUNTAIN_STYLES)
                .contains(AscentStyle.SOLO, AscentStyle.FREE_SOLO);
    }

    @Test
    void shouldOfferSoloOnRopedRock() {
        assertThat(AscentDiscipline.SPORT.allowedStyles())
                .contains(AscentStyle.SOLO, AscentStyle.FREE_SOLO, AscentStyle.TR);
    }

    /**
     * Mirrors {@code chk_climbing_ascents_trad_style}. Trad shares no style with anything else:
     * sport's OS/FLASH/RP cannot say where the ascent was worked from, and toprope and solo are
     * out because a trad logbook records leads.
     */
    @Test
    void shouldOfferOnlyTheGroundUpDialectForTrad() {
        assertThat(AscentDiscipline.TRAD.allowedStyles()).containsExactlyInAnyOrder(
                AscentStyle.OS_GU, AscentStyle.FLASH_GU, AscentStyle.GU, AscentStyle.HP);
    }

    /** The other half of the same CHECK: the dialect belongs to trad and to nothing else. */
    @Test
    void shouldKeepTheGroundUpDialectOutOfEveryOtherDiscipline() {
        for (AscentStyle style : List.of(AscentStyle.OS_GU, AscentStyle.FLASH_GU,
                AscentStyle.GU, AscentStyle.HP)) {
            assertThat(AscentDiscipline.SPORT.allowedStyles()).doesNotContain(style);
            assertThat(AscentDiscipline.BOULDER.allowedStyles()).doesNotContain(style);
            assertThat(AscentDiscipline.MOUNTAIN_STYLES).doesNotContain(style);
        }
    }

    /** An onsight is an onsight in either dialect — the onsight rate reads both. */
    @Test
    void shouldTreatBothOnsightDialectsAsOnsightsAndFirstTries() {
        assertThat(AscentStyle.OS.isOnsight()).isTrue();
        assertThat(AscentStyle.OS_GU.isOnsight()).isTrue();
        assertThat(AscentStyle.GU.isOnsight()).isFalse();

        assertThat(AscentStyle.OS_GU.isFirstTry()).isTrue();
        assertThat(AscentStyle.FLASH_GU.isFirstTry()).isTrue();
        assertThat(AscentStyle.GU.isFirstTry()).isFalse();
        assertThat(AscentStyle.HP.isFirstTry()).isFalse();

        assertThat(AscentStyle.GU.isWorkedSend()).isTrue();
        assertThat(AscentStyle.HP.isWorkedSend()).isTrue();
        assertThat(AscentStyle.RP.isWorkedSend()).isTrue();
        assertThat(AscentStyle.TR.isWorkedSend()).isFalse();
    }

    /** Mirrors {@code chk_climbing_ascents_rock_style}: aid belongs to the mountains only. */
    @Test
    void shouldOfferAidInTheMountainsAndNowhereElse() {
        assertThat(AscentDiscipline.MOUNTAIN_STYLES).contains(AscentStyle.A0);
        assertThat(AscentDiscipline.SPORT.allowedStyles()).doesNotContain(AscentStyle.A0);
        assertThat(AscentDiscipline.TRAD.allowedStyles()).doesNotContain(AscentStyle.A0);
        assertThat(AscentDiscipline.BOULDER.allowedStyles()).doesNotContain(AscentStyle.A0);
    }

    @Test
    void shouldRankStylesFromAidToFreeSolo() {
        assertThat(AscentStyle.FREE_SOLO.purity()).isGreaterThan(AscentStyle.SOLO.purity());
        assertThat(AscentStyle.SOLO.purity()).isGreaterThan(AscentStyle.OS.purity());
        assertThat(AscentStyle.OS.purity()).isGreaterThan(AscentStyle.FLASH.purity());
        assertThat(AscentStyle.FLASH.purity()).isGreaterThan(AscentStyle.RP.purity());
        assertThat(AscentStyle.RP.purity()).isGreaterThan(AscentStyle.TR.purity());
        assertThat(AscentStyle.TR.purity()).isGreaterThan(AscentStyle.A0.purity());
    }

    /** Drives the order of the trad dropdown and of its pyramid legend: OS GU, Flash GU, GU, HP. */
    @Test
    void shouldRankTheTradDialectFromHeadpointToGroundUpOnsight() {
        assertThat(AscentStyle.OS_GU.purity()).isGreaterThan(AscentStyle.FLASH_GU.purity());
        assertThat(AscentStyle.FLASH_GU.purity()).isGreaterThan(AscentStyle.GU.purity());
        assertThat(AscentStyle.GU.purity()).isGreaterThan(AscentStyle.HP.purity());
    }

    @Test
    void shouldTreatOnlyOnsightAndFlashAsFirstTry() {
        assertThat(AscentStyle.OS.isFirstTry()).isTrue();
        assertThat(AscentStyle.FLASH.isFirstTry()).isTrue();
        assertThat(AscentStyle.RP.isFirstTry()).isFalse();
        assertThat(AscentStyle.TR.isFirstTry()).isFalse();
        // Solo says who was there, not how many goes it took
        assertThat(AscentStyle.SOLO.isFirstTry()).isFalse();
        assertThat(AscentStyle.FREE_SOLO.isFirstTry()).isFalse();
    }
}
