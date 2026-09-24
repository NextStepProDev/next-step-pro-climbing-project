package pl.nextsteppro.climbing.domain.trainingrequest;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The hours a client proposed, as a plain value — safe to hand to an {@code @Async} mail, which
 * runs after the request's persistence context has closed.
 */
public record ProposedTerm(LocalDate date, LocalTime startTime, LocalTime endTime) {}
