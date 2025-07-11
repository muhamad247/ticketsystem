package dev.moghrabi.tickets.ticket;

import dev.moghrabi.tickets.user.AppUser;

/**
 * Wird synchron in derselben Transaktion verarbeitet (siehe NotificationListener).
 * Scheitert die Änderung, verschwinden damit auch die Benachrichtigungen.
 *
 * @param staffOnly true bei internen Notizen: die meldende Person wird nicht benachrichtigt
 */
public record TicketChangedEvent(Ticket ticket, AppUser actor, HistoryEntry.Type type, String message, boolean staffOnly) {
}
