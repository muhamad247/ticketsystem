package dev.moghrabi.tickets.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /**
     * Lädt Melder und Bearbeiter gleich mit. Ohne das würde die Liste für jede
     * Zeile zwei zusätzliche Abfragen auslösen (N+1-Problem).
     */
    @Override
    @EntityGraph(attributePaths = {"reporter", "assignee"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);
}
