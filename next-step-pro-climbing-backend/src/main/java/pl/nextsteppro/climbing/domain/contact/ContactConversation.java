package pl.nextsteppro.climbing.domain.contact;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * One running conversation between the team and one person - an account or a guest address.
 * See V108 for why it is one per person rather than one per topic.
 */
@Entity
@Table(name = "contact_conversations")
public class ContactConversation {

    public static final int MAX_GUEST_NAME_LENGTH = 100;
    public static final int MAX_GUEST_EMAIL_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @Nullable
    private User user;

    @Column(name = "guest_email")
    @Nullable
    private String guestEmail;

    @Column(name = "guest_name")
    @Nullable
    private String guestName;

    @Column(name = "last_inbound_at", nullable = false)
    private Instant lastInboundAt;

    @Column(name = "last_reply_at")
    @Nullable
    private Instant lastReplyAt;

    @Column(name = "staff_read_at")
    @Nullable
    private Instant staffReadAt;

    @Column(name = "user_seen_at")
    @Nullable
    private Instant userSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContactConversation() {}

    private ContactConversation(@Nullable User user, @Nullable String guestEmail, @Nullable String guestName,
                                Instant now) {
        this.user = user;
        this.guestEmail = guestEmail;
        this.guestName = guestName;
        this.lastInboundAt = now;
        this.createdAt = now;
    }

    public static ContactConversation forUser(User user, Instant now) {
        return new ContactConversation(user, null, null, now);
    }

    public static ContactConversation forGuest(String email, String name, Instant now) {
        return new ContactConversation(null, email, name, now);
    }

    /** A new message from the person: lights the coach's badge until somebody reads it. */
    public void recordInbound(Instant at) {
        if (at.isAfter(lastInboundAt)) {
            lastInboundAt = at;
        }
    }

    /**
     * A reply from the team. Replying is also reading - the coach answered what was on the screen,
     * and a badge still lit after that is a badge nobody trusts.
     */
    public void recordReply(Instant at) {
        lastReplyAt = at;
        markReadByStaff(at);
    }

    public void markReadByStaff(Instant at) {
        staffReadAt = at;
    }

    public void markSeenByUser(Instant at) {
        userSeenAt = at;
    }

    public void renameGuest(String name) {
        this.guestName = name;
    }

    /**
     * Turns a guest conversation into the conversation of the account that has just confirmed
     * this address. Only for a person who has no conversation of their own yet; the other case
     * moves the messages instead (see {@code ContactConversationClaim}).
     */
    public void assignTo(User owner) {
        this.user = owner;
        this.guestEmail = null;
        this.guestName = null;
    }

    /** Folds the clocks of a conversation merged into this one, so neither side loses an unread. */
    public void absorbClocks(ContactConversation other) {
        recordInbound(other.lastInboundAt);
        if (other.lastReplyAt != null && (lastReplyAt == null || other.lastReplyAt.isAfter(lastReplyAt))) {
            lastReplyAt = other.lastReplyAt;
        }
    }

    public boolean isUnreadByStaff() {
        return staffReadAt == null || lastInboundAt.isAfter(staffReadAt);
    }

    public boolean isUnseenByUser() {
        return lastReplyAt != null && (userSeenAt == null || lastReplyAt.isAfter(userSeenAt));
    }

    /** The last word is the team's - nothing is waiting for an answer. */
    public boolean isAnswered() {
        return lastReplyAt != null && !lastReplyAt.isBefore(lastInboundAt);
    }

    public UUID getId() {
        return id;
    }

    public @Nullable User getUser() {
        return user;
    }

    public @Nullable String getGuestEmail() {
        return guestEmail;
    }

    public @Nullable String getGuestName() {
        return guestName;
    }

    public Instant getLastInboundAt() {
        return lastInboundAt;
    }

    public @Nullable Instant getLastReplyAt() {
        return lastReplyAt;
    }

    public @Nullable Instant getStaffReadAt() {
        return staffReadAt;
    }

    public @Nullable Instant getUserSeenAt() {
        return userSeenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
