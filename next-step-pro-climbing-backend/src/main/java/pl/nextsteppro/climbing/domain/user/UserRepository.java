package pl.nextsteppro.climbing.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
