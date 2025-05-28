package dev.moghrabi.tickets.ticket;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryRepository extends JpaRepository<HistoryEntry, Long> {

    List<HistoryEntry> findByTicketOrderByAtAscIdAsc(Ticket ticket);
}
