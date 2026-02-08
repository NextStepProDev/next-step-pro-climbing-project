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

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredTokens(Instant cutoff);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.user.id = :userId AND t.tokenType = :tokenType")
    void deleteByUserIdAndTokenType(UUID userId, TokenType tokenType);

    @Query("SELECT COUNT(t) > 0 FROM AuthToken t WHERE t.user.id = :userId AND t.tokenType = :tokenType " +
           "AND t.createdAt > :since AND t.usedAt IS NULL")
    boolean hasRecentUnusedToken(UUID userId, TokenType tokenType, Instant since);

    void deleteByUserId(UUID userId);
}
