package pl.nextsteppro.climbing.api.ascent;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingAscentRepository;
import pl.nextsteppro.climbing.domain.climbingascent.PublicAscentRow;

import java.util.List;

/**
 * The public "recent ascents" feed shown on the news page.
 *
 * <p>Separate from {@link AscentService} because it answers a different question with different
 * rules: it reads across every climber, returns no private fields, and is served to visitors who
 * are not logged in at all. Keeping it apart means the personal-logbook service never has a
 * method that skips the owner check.
 *
 * <p>Cached, unlike the personal statistics: this one is hit by anonymous traffic on a public
 * page, and a two-minute-old list of last week's sends is not wrong in any way that matters. The
 * cache is evicted whenever a logbook entry changes, so a fresh send still shows up promptly.
 */
@Service
@Transactional(readOnly = true)
public class PublicAscentService {

    /** Ten, per the brief: enough to show the place is alive, short enough to stay scannable. */
    static final int RECENT_LIMIT = 10;

    /** One cache entry, so the name is a constant rather than a key nobody can guess later. */
    static final String CACHE = "publicAscents";
    static final String CACHE_KEY = "'recent'";

    private final ClimbingAscentRepository ascentRepository;

    public PublicAscentService(ClimbingAscentRepository ascentRepository) {
        this.ascentRepository = ascentRepository;
    }

    @Cacheable(value = CACHE, key = CACHE_KEY)
    public List<PublicAscentDto> getRecent() {
        return ascentRepository.findRecentPublic(PageRequest.of(0, RECENT_LIMIT)).stream()
                .map(PublicAscentService::toDto)
                .toList();
    }

    private static PublicAscentDto toDto(PublicAscentRow row) {
        return new PublicAscentDto(
                row.id(),
                (row.firstName() + " " + row.lastName()).trim(),
                row.climbedOn(),
                row.terrain().name(),
                row.discipline() != null ? row.discipline().name() : null,
                row.grade().scale().name(),
                row.grade().label(),
                row.grade().rank(),
                row.style().name(),
                row.area(),
                row.crag(),
                row.routeName());
    }
}
