package dev.moghrabi.tickets.ticket;

import java.net.URI;
import java.util.List;

import dev.moghrabi.tickets.ticket.TicketDtos.AssignRequest;
import dev.moghrabi.tickets.ticket.TicketDtos.CommentRequest;
import dev.moghrabi.tickets.ticket.TicketDtos.CommentResponse;
import dev.moghrabi.tickets.ticket.TicketDtos.CreateTicketRequest;
import dev.moghrabi.tickets.ticket.TicketDtos.HistoryResponse;
import dev.moghrabi.tickets.ticket.TicketDtos.PageResponse;
import dev.moghrabi.tickets.ticket.TicketDtos.PriorityChangeRequest;
import dev.moghrabi.tickets.ticket.TicketDtos.StatusChangeRequest;
import dev.moghrabi.tickets.ticket.TicketDtos.TicketDetails;
import dev.moghrabi.tickets.ticket.TicketDtos.TicketSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets")
public class TicketController {

    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping
    @Operation(summary = "Ticket melden")
    public ResponseEntity<TicketDetails> create(@Valid @RequestBody CreateTicketRequest request, Authentication auth) {
        TicketDetails ticket = tickets.create(request, auth.getName());
        return ResponseEntity.created(URI.create("/api/tickets/" + ticket.id())).body(ticket);
    }

    @GetMapping
    @Operation(summary = "Tickets auflisten (Meldende sehen nur eigene)")
    public PageResponse<TicketSummary> list(@RequestParam(required = false) TicketStatus status,
                                            @RequestParam(required = false) Priority priority,
                                            @RequestParam(defaultValue = "false") boolean assignedToMe,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            Authentication auth) {
        return tickets.list(auth.getName(), status, priority, assignedToMe, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ticket mit Kommentaren und möglichen nächsten Status")
    public TicketDetails get(@PathVariable Long id, Authentication auth) {
        return tickets.get(id, auth.getName());
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Status ändern (Regeln siehe TicketWorkflow)")
    public TicketDetails changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request,
                                      Authentication auth) {
        return tickets.changeStatus(id, request, auth.getName());
    }

    @PatchMapping("/{id}/assignee")
    @Operation(summary = "Zuweisen (nur Support)")
    public TicketDetails assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request, Authentication auth) {
        return tickets.assign(id, request, auth.getName());
    }

    @PatchMapping("/{id}/priority")
    @Operation(summary = "Priorität ändern, die Frist wird neu berechnet (nur Support)")
    public TicketDetails changePriority(@PathVariable Long id, @Valid @RequestBody PriorityChangeRequest request,
                                        Authentication auth) {
        return tickets.changePriority(id, request, auth.getName());
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Kommentar oder interne Notiz hinzufügen")
    public CommentResponse comment(@PathVariable Long id, @Valid @RequestBody CommentRequest request,
                                   Authentication auth) {
        return tickets.comment(id, request, auth.getName());
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Verlauf aller Änderungen")
    public List<HistoryResponse> history(@PathVariable Long id, Authentication auth) {
        return tickets.history(id, auth.getName());
    }
}
