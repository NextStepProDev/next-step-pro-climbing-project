package pl.nextsteppro.climbing.api.auth;

/**
 * Sign-in refused because the address was never confirmed.
 *
 * <p>Exists purely so the answer carries a code of its own. Every other conflict leaves the client
 * with {@code CONFLICT}, and this is the one refusal that has somewhere for the user to go — the
 * page can offer a new confirmation link on the spot instead of leaving them to find it. Matching
 * on the message text would have meant matching three translations of it.
 */
public class EmailNotVerifiedException extends IllegalStateException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
