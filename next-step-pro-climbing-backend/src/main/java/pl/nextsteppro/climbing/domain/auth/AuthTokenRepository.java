package pl.nextsteppro.climbing.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHashAndTokenType(String tokenHash, TokenType tokenType);

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

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredTokens(Instant cutoff);

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
