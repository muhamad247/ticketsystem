package dev.moghrabi.tickets.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import dev.moghrabi.tickets.error.ApiException;
import dev.moghrabi.tickets.user.AppUser;
import dev.moghrabi.tickets.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Benachrichtigungen")
public class NotificationController {

    public record NotificationResponse(Long id, Long ticketId, String message, Instant createdAt, boolean read) {
    }

    private final NotificationRepository notifications;
    private final UserService users;
    private final Clock clock;

    public NotificationController(NotificationRepository notifications, UserService users, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Eigene Benachrichtigungen, neueste zuerst (max. 50)")
    public List<NotificationResponse> mine(@RequestParam(defaultValue = "false") boolean unreadOnly, Authentication auth) {
        AppUser me = users.byUsername(auth.getName());
        List<Notification> result = unreadOnly
                ? notifications.findTop50ByRecipientAndReadAtIsNullOrderByCreatedAtDescIdDesc(me)
                : notifications.findTop50ByRecipientOrderByCreatedAtDescIdDesc(me);
        return result.stream()
                .map(n -> new NotificationResponse(n.getId(), n.getTicket().getId(), n.getMessage(), n.getCreatedAt(),
                        n.getReadAt() != null))
                .toList();
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    @Operation(summary = "Als gelesen markieren")
    public void markRead(@PathVariable Long id, Authentication auth) {
        AppUser me = users.byUsername(auth.getName());
        notifications.findByIdAndRecipient(id, me)
                .orElseThrow(() -> ApiException.notFound("Benachrichtigung " + id))
                .markRead(clock.instant());
    }
}
