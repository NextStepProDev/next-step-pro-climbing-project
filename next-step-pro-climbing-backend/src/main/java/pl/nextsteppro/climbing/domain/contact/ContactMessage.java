package pl.nextsteppro.climbing.domain.contact;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One message in a {@link ContactConversation}: from the person, or a reply from the team. */
@Entity
@Table(name = "contact_messages")
public class ContactMessage {

    public static final int MAX_BODY_LENGTH = 4000;
    public static final int MAX_COURSE_TITLE_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ContactConversation conversation;

    @Column(name = "from_staff", nullable = false)
    private boolean fromStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_author_id")
    @Nullable
    private User staffAuthor;

    @Column(name = "body", nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false)
    private ContactContextType contextType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    @Nullable
    private Course course;

    @Column(name = "course_title")
    @Nullable
    private String courseTitle;

    @Column(name = "context_date")
    @Nullable
    private LocalDate contextDate;

    @Column(name = "language", nullable = false)
    private String language;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContactMessage() {}

    private ContactMessage(ContactConversation conversation, boolean fromStaff, @Nullable User staffAuthor,
                           String body, ContactContextType contextType, @Nullable Course course,
                           @Nullable String courseTitle, @Nullable LocalDate contextDate, String language,
                           Instant createdAt) {
        this.conversation = conversation;
        this.fromStaff = fromStaff;
        this.staffAuthor = staffAuthor;
        this.body = body;
        this.contextType = contextType;
        this.course = course;
        this.courseTitle = courseTitle;
        this.contextDate = contextDate;
        this.language = language;
        this.createdAt = createdAt;
    }

    public static ContactMessage general(ContactConversation conversation, String body, String language, Instant at) {
        return new ContactMessage(conversation, false, null, body, ContactContextType.GENERAL,
            null, null, null, language, at);
    }

    public static ContactMessage aboutCourse(ContactConversation conversation, String body, Course course,
                                             String language, Instant at) {
        String title = course.getTitle();
        String capped = title.length() > MAX_COURSE_TITLE_LENGTH ? title.substring(0, MAX_COURSE_TITLE_LENGTH) : title;
        return new ContactMessage(conversation, false, null, body, ContactContextType.COURSE,
            course, capped, null, language, at);
    }

    /** @param day the day the person had selected, or null when they wrote from a week or month view */
    public static ContactMessage aboutCalendar(ContactConversation conversation, String body, @Nullable LocalDate day,
                                               String language, Instant at) {
        return new ContactMessage(conversation, false, null, body, ContactContextType.CALENDAR,
            null, null, day, language, at);
    }

    public static ContactMessage reply(ContactConversation conversation, User author, String body,
                                       String language, Instant at) {
        return new ContactMessage(conversation, true, author, body, ContactContextType.GENERAL,
            null, null, null, language, at);
    }

    /** Re-homes the message when a guest conversation is merged into an account's. */
    public void moveTo(ContactConversation target) {
        this.conversation = target;
    }

    public UUID getId() {
        return id;
    }

    public ContactConversation getConversation() {
        return conversation;
    }

    public boolean isFromStaff() {
        return fromStaff;
    }

    public @Nullable User getStaffAuthor() {
        return staffAuthor;
    }

    public String getBody() {
        return body;
    }

    public ContactContextType getContextType() {
        return contextType;
    }

    public @Nullable Course getCourse() {
        return course;
    }

    public @Nullable String getCourseTitle() {
        return courseTitle;
    }

    public @Nullable LocalDate getContextDate() {
        return contextDate;
    }

    public String getLanguage() {
        return language;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
