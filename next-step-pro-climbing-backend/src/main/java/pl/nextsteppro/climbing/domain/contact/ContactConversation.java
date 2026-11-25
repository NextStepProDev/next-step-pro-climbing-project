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

    /** The person's newest message; null while only the team has written (V109). */
    @Column(name = "last_inbound_at")
    @Nullable
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

    /**
     * The newest message in EITHER direction - what the admin list is ordered by, like a
     * messenger. Kept by {@link #recordInbound} and {@link #recordReply}; never set directly.
     */
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    /** When the person was last reminded by mail of an unseen answer (V110); one reminder per answer. */
    @Column(name = "unseen_reminder_sent_at")
    @Nullable
    private Instant unseenReminderSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContactConversation() {}

    private ContactConversation(@Nullable User user, @Nullable String guestEmail, @Nullable String guestName,
                                @Nullable Instant firstInbound, @Nullable Instant firstReply, Instant now) {
        this.user = user;
        this.guestEmail = guestEmail;
        this.guestName = guestName;
        this.lastInboundAt = firstInbound;
        this.lastReplyAt = firstReply;
        // The team's own first message is, like any reply, also reading (see recordReply).
        this.staffReadAt = firstReply;
        this.lastActivityAt = now;
        this.createdAt = now;
    }

    /** The person writes first. */
    public static ContactConversation forUser(User user, Instant now) {
        return new ContactConversation(user, null, null, now, null, now);
    }

    public static ContactConversation forGuest(String email, String name, Instant now) {
        return new ContactConversation(null, email, name, now, null, now);
    }

    /**
     * The team writes first, from the message centre. Only an account: a guest has no app to read
     * it in, so starting one would mean an unsolicited mail to an address from a contact form.
     * Both clocks are set at construction, so the row satisfies V109's "somebody wrote" check on
     * its very first INSERT.
     */
    public static ContactConversation startedByStaff(User user, Instant now) {
        return new ContactConversation(user, null, null, null, now, now);
    }

    /** A new message from the person: lights the coach's badge until somebody reads it. */
    public void recordInbound(Instant at) {
        if (lastInboundAt == null || at.isAfter(lastInboundAt)) {
            lastInboundAt = at;
        }
        touch(at);
    }

    /**
     * A reply from the team. Replying is also reading - the coach answered what was on the screen,
     * and a badge still lit after that is a badge nobody trusts.
     */
    public void recordReply(Instant at) {
        lastReplyAt = at;
        markReadByStaff(at);
        touch(at);
    }

    private void touch(Instant at) {
        if (at.isAfter(lastActivityAt)) {
            lastActivityAt = at;
        }
    }

    public void markReadByStaff(Instant at) {
        staffReadAt = at;
    }

    public void markSeenByUser(Instant at) {
        userSeenAt = at;
    }

    public void markUnseenReminderSent(Instant at) {
        unseenReminderSentAt = at;
    }

    public @Nullable Instant getUnseenReminderSentAt() {
        return unseenReminderSentAt;
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
        if (other.lastInboundAt != null) {
            recordInbound(other.lastInboundAt);
        }
        if (other.lastReplyAt != null && (lastReplyAt == null || other.lastReplyAt.isAfter(lastReplyAt))) {
            lastReplyAt = other.lastReplyAt;
        }
        touch(other.lastActivityAt);
    }

    /** Something from the person nobody on the team has read. A conversation only the team wrote in has none. */
    public boolean isUnreadByStaff() {
        return lastInboundAt != null && (staffReadAt == null || lastInboundAt.isAfter(staffReadAt));
    }

    public boolean isUnseenByUser() {
        return lastReplyAt != null && (userSeenAt == null || lastReplyAt.isAfter(userSeenAt));
    }

    /**
     * The last word is the team's - nothing is waiting for an answer. True for a conversation the
     * team started and the person has not answered yet: the ball is in their court.
     */
    public boolean isAnswered() {
        return lastReplyAt != null && (lastInboundAt == null || !lastReplyAt.isBefore(lastInboundAt));
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

    public @Nullable Instant getLastInboundAt() {
        return lastInboundAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
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
