package pl.nextsteppro.climbing.infrastructure.i18n;

/**
 * An exception whose reply text is a message KEY, translated by {@code GlobalExceptionHandler} in
 * the caller's language.
 *
 * <p>For code that has no {@link MessageService} at hand — the storage layer, and the services
 * that never needed one — and must not grow one just to refuse a request. A literal English
 * {@code new IllegalArgumentException("Album not found")} reached the screen word for word, in
 * front of a Polish admin and a Spanish client alike. {@code getMessage()} returns the key, which
 * is what the log should carry anyway: stable, greppable, and free of the caller's input.
 */
public interface LocalizedMessage {

    String messageKey();

    Object[] messageArgs();

    /** A request the server understood and refuses — 400. */
    final class Rejected extends IllegalArgumentException implements LocalizedMessage {
        private final String key;
        private final Object[] args;

        public Rejected(String key, Object... args) {
            super(key);
            this.key = key;
            this.args = args;
        }

        @Override public String messageKey() { return key; }
        @Override public Object[] messageArgs() { return args.clone(); }
    }

    /** A request that collides with the current state — 409. */
    final class Conflict extends IllegalStateException implements LocalizedMessage {
        private final String key;
        private final Object[] args;

        public Conflict(String key, Object... args) {
            super(key);
            this.key = key;
            this.args = args;
        }

        public Conflict(String key, Throwable cause) {
            super(key, cause);
            this.key = key;
            this.args = new Object[0];
        }

        @Override public String messageKey() { return key; }
        @Override public Object[] messageArgs() { return args.clone(); }
    }
}
