package pl.nextsteppro.climbing.infrastructure.mail;

import java.util.List;

/**
 * A gallery block as the newsletter draws it: a row of the album's first photos and a button to
 * the article. Plain values, read in the caller's transaction, because the mail is built on the
 * campaign thread, where no persistence context is open.
 *
 * @param photoUrls  at most {@link #PREVIEW_PHOTOS} public photo URLs, in album order
 * @param photoCount every photo in the album, for the button label
 */
public record NewsletterGallery(String albumName, List<String> photoUrls, int photoCount) {

    /** A mail has no lightbox, so it shows one row and sends the reader to the site for the rest. */
    public static final int PREVIEW_PHOTOS = 4;
}
