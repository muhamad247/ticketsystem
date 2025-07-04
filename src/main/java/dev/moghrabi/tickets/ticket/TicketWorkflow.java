package dev.moghrabi.tickets.ticket;

import static dev.moghrabi.tickets.ticket.TicketStatus.CLOSED;
import static dev.moghrabi.tickets.ticket.TicketStatus.IN_PROGRESS;
import static dev.moghrabi.tickets.ticket.TicketStatus.OPEN;
import static dev.moghrabi.tickets.ticket.TicketStatus.RESOLVED;
import static dev.moghrabi.tickets.ticket.TicketStatus.WAITING;
import static dev.moghrabi.tickets.user.Role.ADMIN;
import static dev.moghrabi.tickets.user.Role.AGENT;
import static dev.moghrabi.tickets.user.Role.REPORTER;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.moghrabi.tickets.error.ApiException;
import dev.moghrabi.tickets.user.Role;

/**
 * Erlaubte Statusübergänge und wer sie auslösen darf, als Tabelle an einer
 * Stelle statt verteilt über if-Abfragen im Service.
 *
 * <pre>
 *   OPEN        -> IN_PROGRESS  (Support übernimmt)      | OPEN -> CLOSED (nur Admin: Duplikat, Spam)
 *   IN_PROGRESS -> WAITING      (Rückfrage an Meldende)  | IN_PROGRESS -> RESOLVED
 *   WAITING     -> IN_PROGRESS  (Rückmeldung ist da)     | WAITING -> RESOLVED
 *   RESOLVED    -> CLOSED       (Lösung bestätigt)       | RESOLVED -> IN_PROGRESS (wieder geöffnet)
 * </pre>
 */
public final class TicketWorkflow {

    private static final Map<TicketStatus, Map<TicketStatus, Set<Role>>> RULES = new EnumMap<>(TicketStatus.class);

    static {
        RULES.put(OPEN, Map.of(
                IN_PROGRESS, Set.of(AGENT, ADMIN),
                CLOSED, Set.of(ADMIN))); // Duplikat oder Spam
        RULES.put(IN_PROGRESS, Map.of(
                WAITING, Set.of(AGENT, ADMIN),
                RESOLVED, Set.of(AGENT, ADMIN)));
        RULES.put(WAITING, Map.of(
                IN_PROGRESS, Set.of(REPORTER, AGENT, ADMIN), // Rückmeldung ist da
                RESOLVED, Set.of(AGENT, ADMIN)));
        RULES.put(RESOLVED, Map.of(
                CLOSED, Set.of(REPORTER, ADMIN), // Lösung bestätigt
                IN_PROGRESS, Set.of(REPORTER, AGENT, ADMIN))); // wieder geöffnet
        RULES.put(CLOSED, Map.of());
    }

    private TicketWorkflow() {
    }

    /** Wirft 409, wenn der Übergang nicht existiert, und 403, wenn die Rolle ihn nicht auslösen darf. */
    public static void check(TicketStatus from, TicketStatus to, Role role) {
        Set<Role> allowed = RULES.get(from).get(to);
        if (allowed == null) {
            throw ApiException.conflict("Von %s nach %s gibt es keinen Übergang".formatted(from, to));
        }
        if (!allowed.contains(role)) {
            throw ApiException.forbidden("Die Rolle %s darf ein Ticket nicht von %s nach %s setzen".formatted(role, from, to));
        }
    }

    /** Für die Oberfläche: welche Knöpfe soll diese Rolle sehen? */
    public static List<TicketStatus> nextFor(TicketStatus from, Role role) {
        return RULES.get(from).entrySet().stream()
                .filter(e -> e.getValue().contains(role))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
