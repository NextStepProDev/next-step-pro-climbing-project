package pl.nextsteppro.climbing.api.mysubscription;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.util.UUID;

/**
 * Under {@code /api/user} on purpose: it is the caller's own account, it shares that rate-limit
 * bucket, and there is no id in the path — the token is the only address, so there is no other
 * person's subscription to ask for.
 */
@RestController
@RequestMapping("/api/user/subscription")
@Tag(name = "User", description = "User profile")
public class MySubscriptionController {

    private final MySubscriptionService service;

    MySubscriptionController(MySubscriptionService service) {
        this.service = service;
    }

    @Operation(summary = "My coaching subscription",
        description = "The running (or most recent) subscription, its monthly fees for the last "
            + "twelve months plus anything older still owed, and what is due. Empty when the caller "
            + "never had one.")
    @GetMapping
    public ResponseEntity<MySubscriptionDto> mine(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(service.forUser(userId));
    }
}
