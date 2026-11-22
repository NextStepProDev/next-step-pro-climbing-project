package pl.nextsteppro.climbing.domain.contact;

/** Where an inbound message was written, which is what it is about. */
public enum ContactContextType {
    /** The contact page, or a reply in the conversation itself. */
    GENERAL,
    /** "Ask about this course" on a course page; the message names the course. */
    COURSE,
    /** "Write to us" under the calendar; the message names the day the person was looking at. */
    CALENDAR
}
