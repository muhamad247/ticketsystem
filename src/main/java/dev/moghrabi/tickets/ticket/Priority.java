package dev.moghrabi.tickets.ticket;

import java.time.Duration;

/** Priorität mit Reaktionszeit (SLA): bis dahin soll das Ticket gelöst sein. */
public enum Priority {
    LOW(Duration.ofDays(7)),
    MEDIUM(Duration.ofDays(3)),
    HIGH(Duration.ofDays(1)),
    CRITICAL(Duration.ofHours(4));

    private final Duration sla;

    Priority(Duration sla) {
        this.sla = sla;
    }

    public Duration sla() {
        return sla;
    }
}
