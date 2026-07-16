package pl.nextsteppro.climbing.domain.user;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    // Account lockout policy
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "avatar_filename")
    @Nullable
    private String avatarFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "oauth_provider")
    @Nullable
    private String oauthProvider;

    @Column(name = "oauth_id")
    @Nullable
    private String oauthId;

    @Column(name = "password_hash")
    @Nullable
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    @Nullable
    private Instant emailVerifiedAt;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = true;

    @Column(name = "newsletter_subscribed", nullable = false)
    private boolean newsletterSubscribed = false;

    @Column(name = "newsletter_choice_made", nullable = false)
    private boolean newsletterChoiceMade = false;

    @Column(name = "newsletter_subscribed_at")
    @Nullable
    private Instant newsletterSubscribedAt;

    @Column(name = "preferred_language", nullable = false)
    private String preferredLanguage = "en";

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    @Nullable
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // "Read" marker for admin notifications about new reservations (panel badge);
    // unused for regular users. A fresh account = nothing unread from before registration.
    @Column(name = "admin_reservations_seen_at", nullable = false)
    private Instant adminReservationsSeenAt = Instant.now();

    // Coach-designated athlete: unlocks the personal training calendar.
    // Toggled by admin; switching off hides the calendar but keeps its data.
    @Column(name = "is_athlete", nullable = false)
    private boolean athlete = false;

    protected User() {}

    public User(String email, String firstName, String lastName, String phone, String nickname) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.nickname = nickname;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    @Nullable
    public String getAvatarFilename() {
        return avatarFilename;
    }

    public void setAvatarFilename(@Nullable String avatarFilename) {
        this.avatarFilename = avatarFilename;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    @Nullable
    public String getOauthProvider() {
        return oauthProvider;
    }

    public void setOauthProvider(@Nullable String oauthProvider) {
        this.oauthProvider = oauthProvider;
    }

    @Nullable
    public String getOauthId() {
        return oauthId;
    }

    public void setOauthId(@Nullable String oauthId) {
        this.oauthId = oauthId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getAdminReservationsSeenAt() {
        return adminReservationsSeenAt;
    }

    public void markAdminReservationsSeen() {
        this.adminReservationsSeenAt = Instant.now();
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isAthlete() {
        return athlete;
    }

    public void setAthlete(boolean athlete) {
        this.athlete = athlete;
    }

    @Nullable
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(@Nullable String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    @Nullable
    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(@Nullable Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        this.emailVerifiedAt = Instant.now();
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public boolean isNewsletterSubscribed() {
        return newsletterSubscribed;
    }

    public void setNewsletterSubscribed(boolean newsletterSubscribed) {
        this.newsletterSubscribed = newsletterSubscribed;
    }

    public boolean isNewsletterChoiceMade() {
        return newsletterChoiceMade;
    }

    public void setNewsletterChoiceMade(boolean newsletterChoiceMade) {
        this.newsletterChoiceMade = newsletterChoiceMade;
    }

    @Nullable
    public Instant getNewsletterSubscribedAt() {
        return newsletterSubscribedAt;
    }

    public void setNewsletterSubscribedAt(@Nullable Instant newsletterSubscribedAt) {
        this.newsletterSubscribedAt = newsletterSubscribedAt;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    // Account lockout methods
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    @Nullable
    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public boolean isAccountLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    public void incrementFailedLoginAttempts() {
        incrementFailedLoginAttempts(Instant.now());
    }

    // Package-private overload for deterministic testing (inject "now")
    void incrementFailedLoginAttempts(Instant now) {
        // If a previous lockout has already expired, start a fresh counting window.
        // Without this, the first wrong password after the lockout expires would push
        // the stale counter past the threshold and re-lock the account immediately.
        if (this.lockedUntil != null && now.isAfter(this.lockedUntil)) {
            this.failedLoginAttempts = 0;
            this.lockedUntil = null;
        }
        this.failedLoginAttempts++;
        // Lock account after too many failed attempts
        if (this.failedLoginAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            this.lockedUntil = now.plus(LOCKOUT_DURATION);
        }
    }

    public long getRemainingLockoutMinutes() {
        if (lockedUntil == null) return 0;
        long seconds = Duration.between(Instant.now(), lockedUntil).getSeconds();
        if (seconds <= 0) return 0;
        return (seconds + 59) / 60;
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }
}
