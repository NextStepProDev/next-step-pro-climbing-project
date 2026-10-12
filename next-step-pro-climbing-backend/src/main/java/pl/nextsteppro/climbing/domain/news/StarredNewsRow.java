package pl.nextsteppro.climbing.domain.news;

import java.time.Instant;
import java.util.UUID;

/** An article somebody starred, with its title — a bare id means nothing to the person reading it. */
public record StarredNewsRow(UUID newsId, String title, Instant starredAt) {}
