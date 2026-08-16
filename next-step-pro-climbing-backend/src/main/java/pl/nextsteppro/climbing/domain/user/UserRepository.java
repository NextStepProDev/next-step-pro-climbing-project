package pl.nextsteppro.climbing.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

    /** Resolves the unsubscribe link from a newsletter to its recipient. */
    Optional<User> findByNewsletterUnsubscribeToken(UUID newsletterUnsubscribeToken);

    boolean existsByEmail(String email);

    List<User> findAllByNewsletterSubscribedTrue();

    /** Coach's roster: users flagged as athletes (personal training calendar). */
    List<User> findAllByAthleteTrueOrderByFirstNameAscLastNameAsc();

    /**
     * Accounts that never confirmed their address and were registered before {@code cutoff}.
     * Serves both retention passes: the reminder reads the [6d, 7d) band, the deletion everything
     * older than 7 days.
     */
    List<User> findAllByEmailVerifiedFalseAndCreatedAtBefore(Instant cutoff);
}
