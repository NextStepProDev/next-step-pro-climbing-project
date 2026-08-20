package pl.nextsteppro.climbing.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
     * Admin notifications: accounts confirmed since this admin last read them (panel badge).
     *
     * <p>Counted off {@code email_verified_at} rather than {@code created_at} so both routes into
     * the service land in it without a branch: the e-mail link stamps it, and so does an OAuth
     * sign-up, which never sees a confirmation link at all. Accounts that never confirmed hold
     * NULL there and drop out on their own — which is the wanted answer, since an unconfirmed
     * account is one nobody can sign in to.
     */
    int countByEmailVerifiedAtAfter(Instant seenAt);

    /**
     * Accounts that never confirmed their address and were registered before {@code cutoff}.
     * Serves both retention passes: the reminder reads the [6d, 7d) band, the deletion everything
     * older than 7 days.
     */
    List<User> findAllByEmailVerifiedFalseAndCreatedAtBefore(Instant cutoff);

    /**
     * Every account as the columns the admin statistics count, newest last.
     *
     * <p>One pass over the whole table by design: every account-level number on that screen
     * (verified, athletes, newsletter split, registrations per month) is a different question about
     * the same rows, and asking each one as its own {@code COUNT(*) FILTER} would be several
     * scans of the same table answering from several moments. The service folds them in one Java
     * pass instead — the same shape as {@code TrainingStatsService}.
     */
    @Query("""
        SELECT new pl.nextsteppro.climbing.domain.user.UserAccountRow(
            u.id, u.createdAt, u.emailVerified, u.role, u.athlete, u.trainingConsentAt,
            u.newsletterSubscribed, u.newsletterChoiceMade)
        FROM User u
        ORDER BY u.createdAt
        """)
    List<UserAccountRow> findAccountRows();
}
