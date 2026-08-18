package pl.nextsteppro.climbing.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    @Query("SELECT t FROM AuthToken t WHERE t.tokenHash = :tokenHash AND t.tokenType = :tokenType " +
           "AND t.expiresAt > :now AND t.usedAt IS NULL")
    Optional<AuthToken> findValidToken(String tokenHash, TokenType tokenType, Instant now);

    /**
     * Like {@link #findValidToken} but also accepts a token whose first use was within a grace
     * window (usedAt after :graceThreshold). Used for refresh-token rotation so concurrent
     * refreshes from several open tabs all succeed instead of the losers being logged out.
     */
    @Query("SELECT t FROM AuthToken t WHERE t.tokenHash = :tokenHash AND t.tokenType = :tokenType " +
           "AND t.expiresAt > :now AND (t.usedAt IS NULL OR t.usedAt > :graceThreshold)")
    Optional<AuthToken> findRefreshableToken(String tokenHash, TokenType tokenType, Instant now, Instant graceThreshold);

    /**
     * Finds a token whatever state it is in — expired, used, both. Every other lookup here filters
     * those out; this one exists so a dead confirmation link can still be traced back to its owner
     * and swapped for a fresh one.
     */
    Optional<AuthToken> findFirstByTokenHashAndTokenTypeOrderByCreatedAtDesc(String tokenHash, TokenType tokenType);

    /**
     * Sweeps expired tokens hourly — except confirmation ones, which are swept by age instead (see
     * {@link #deleteStaleVerificationTokens}). Deleting those on expiry threw away the only record
     * tying a clicked-but-expired link to an account, so the person holding it could no longer be
     * offered a new one.
     */
    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :cutoff AND t.tokenType <> pl.nextsteppro.climbing.domain.auth.TokenType.EMAIL_VERIFICATION")
    int deleteExpiredTokens(Instant cutoff);

    /**
     * Sweeps confirmation tokens by {@code createdAt}, not by expiry: they are meant to outlive
     * their own expiry and go when the account they belong to would go. One rule covers both ends —
     * spent tokens of accounts that did confirm, and dead ones of accounts that never did.
     */
    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.tokenType = pl.nextsteppro.climbing.domain.auth.TokenType.EMAIL_VERIFICATION AND t.createdAt < :cutoff")
    int deleteStaleVerificationTokens(Instant cutoff);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.user.id = :userId AND t.tokenType = :tokenType")
    void deleteByUserIdAndTokenType(UUID userId, TokenType tokenType);

    @Query("SELECT COUNT(t) > 0 FROM AuthToken t WHERE t.user.id = :userId AND t.tokenType = :tokenType " +
           "AND t.createdAt > :since AND t.usedAt IS NULL")
    boolean hasRecentUnusedToken(UUID userId, TokenType tokenType, Instant since);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.user.id = :userId")
    void deleteAllByUserId(UUID userId);
}
