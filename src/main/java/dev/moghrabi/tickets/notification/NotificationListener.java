package dev.moghrabi.tickets.notification;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.moghrabi.tickets.ticket.Ticket;
import dev.moghrabi.tickets.ticket.TicketChangedEvent;
import dev.moghrabi.tickets.user.AppUser;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Entscheidet, wer über eine Änderung informiert wird. Der Auslöser selbst
 * bekommt nie eine Benachrichtigung über die eigene Aktion.
 *
 * Bewusst {@code @EventListener} (synchron, gleiche Transaktion): Ticketänderung
 * und Benachrichtigungen werden gemeinsam gespeichert oder gemeinsam verworfen.
 */
@Component
public class NotificationListener {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationListener(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @EventListener
    public void on(TicketChangedEvent event) {
        Ticket ticket = event.ticket();
        Map<Long, AppUser> recipients = new LinkedHashMap<>(); // nach ID, damit niemand doppelt benachrichtigt wird
        switch (event.type()) {
            case CREATED -> {
                // Neue Tickets landen in der Liste des Supports, eine Benachrichtigung braucht es nicht
            }
            case ASSIGNED -> add(recipients, ticket.getAssignee());
            case STATUS_CHANGED, PRIORITY_CHANGED, COMMENTED -> {
                if (!event.staffOnly()) {
                    add(recipients, ticket.getReporter());
                }
                add(recipients, ticket.getAssignee());
            }
        }
        recipients.remove(event.actor().getId());

        String message = "Ticket #%d „%s“: %s".formatted(ticket.getId(), shorten(ticket.getTitle()), event.message());
        recipients.values().forEach(user -> notifications.save(new Notification(user, ticket, message, clock.instant())));
    }

    private static void add(Map<Long, AppUser> recipients, AppUser user) {
        if (user != null) {
            recipients.putIfAbsent(user.getId(), user);
        }
    }

    private static String shorten(String title) {
        return title.length() <= 60 ? title : title.substring(0, 57) + "...";
    }
}
