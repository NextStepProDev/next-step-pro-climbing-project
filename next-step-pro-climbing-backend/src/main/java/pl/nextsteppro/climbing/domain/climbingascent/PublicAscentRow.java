package pl.nextsteppro.climbing.domain.climbingascent;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One entry of the public "recent ascents" list: what was climbed, and by whom.
 *
 * <p>Carries the climber's name because that is the point of the list — but only for climbers who
 * have not switched {@code users.ascents_public} off, which the query enforces rather than the
 * caller. Nothing else from the logbook travels: no comment, no attempt count, no rating. Those
 * are notes to self, and publishing a name is already the most this list should say about anyone.
 */
public record PublicAscentRow(
        UUID id,
        String firstName,
        String lastName,
        LocalDate climbedOn,
        AscentTerrain terrain,
        /** Null on mountain entries — they have a season instead. */
        @Nullable AscentDiscipline discipline,
        ClimbingGrade grade,
        AscentStyle style,
        String area,
        String crag,
        String routeName) {
}
