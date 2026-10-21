package pl.nextsteppro.climbing.api.admin.reschedule;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Lets a test outside this package drive the coach's answers: the request/response records are
 * package-private, like every DTO in this codebase. A bean in test sources only.
 */
@Component
public class AdminRescheduleTestAccess {

    private final AdminRescheduleService service;

    public AdminRescheduleTestAccess(AdminRescheduleService service) {
        this.service = service;
    }

    /** @return how many OTHER participants were mailed about the move */
    public int accept(UUID adminId, UUID requestId) {
        return service.accept(adminId, requestId).notifiedOthers();
    }

    public void reject(UUID adminId, UUID requestId, @Nullable String note, boolean notifyUser) {
        service.reject(adminId, requestId, new RejectRescheduleRequest(note, notifyUser));
    }
}
