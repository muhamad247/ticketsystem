package dev.moghrabi.tickets.ticket;

public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    /** Support wartet auf Rückmeldung der meldenden Person */
    WAITING,
    RESOLVED,
    CLOSED;

    public boolean isDone() {
        return this == RESOLVED || this == CLOSED;
    }
}
