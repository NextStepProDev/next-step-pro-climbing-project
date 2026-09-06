package pl.nextsteppro.climbing.domain.personaltraining;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A booked session whose thread has unread messages — exactly one of the two ids is set.
 *
 * <p>One projection rather than two id queries because the calendar range asks this question once
 * per view and both halves come off the same rows.
 */
public record SessionCommentTarget(@Nullable UUID slotId, @Nullable UUID eventId) {}
