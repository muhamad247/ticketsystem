package dev.moghrabi.tickets.ticket;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTicketOrderByCreatedAtAscIdAsc(Ticket ticket);
}
