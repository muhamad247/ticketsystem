package dev.moghrabi.tickets.ticket;

import dev.moghrabi.tickets.user.AppUser;
import org.springframework.data.jpa.domain.Specification;

/** Bausteine für die Ticketliste. Ein Filter mit null-Wert schränkt nicht ein. */
final class TicketFilters {

    private TicketFilters() {
    }

    static Specification<Ticket> status(TicketStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    static Specification<Ticket> priority(Priority priority) {
        return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    static Specification<Ticket> reporter(AppUser reporter) {
        return (root, query, cb) -> reporter == null ? null : cb.equal(root.get("reporter"), reporter);
    }

    static Specification<Ticket> assignee(AppUser assignee) {
        return (root, query, cb) -> assignee == null ? null : cb.equal(root.get("assignee"), assignee);
    }
}
