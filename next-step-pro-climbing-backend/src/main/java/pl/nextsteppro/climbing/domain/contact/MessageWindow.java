package pl.nextsteppro.climbing.domain.contact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A page of a conversation as the chat window shows it: oldest first, plus whether there is more
 * above. Both sides (the team's panel and the person's chat) page the same way, so the size and
 * the "one extra row tells us there is more" trick live here once.
 *
 * @param messages oldest first
 * @param hasOlder whether a message older than the first one exists
 */
public record MessageWindow(List<ContactMessage> messages, boolean hasOlder) {

    /** What the window opens with and what one scroll to the top adds. */
    public static final int PAGE_SIZE = 30;

    /** The most one poll returns; the next poll carries on from the last of them. */
    public static final int NEWER_CAP = 100;

    /** From a newest-first read of up to {@code PAGE_SIZE + 1} rows: the extra row only says "there is more". */
    static MessageWindow ofNewestFirst(List<ContactMessage> newestFirst) {
        boolean hasOlder = newestFirst.size() > PAGE_SIZE;
        List<ContactMessage> page = new ArrayList<>(hasOlder ? newestFirst.subList(0, PAGE_SIZE) : newestFirst);
        Collections.reverse(page);
        return new MessageWindow(List.copyOf(page), hasOlder);
    }
}
