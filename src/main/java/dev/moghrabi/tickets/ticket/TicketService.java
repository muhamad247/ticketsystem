package dev.moghrabi.tickets.ticket;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import dev.moghrabi.tickets.error.ApiException;
import dev.moghrabi.tickets.ticket.HistoryEntry.Type;
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
import dev.moghrabi.tickets.user.AppUser;
import dev.moghrabi.tickets.user.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private static final String INTERNAL = "intern";

    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final HistoryRepository history;
    private final UserService users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TicketService(TicketRepository tickets, CommentRepository comments, HistoryRepository history,
                         UserService users, ApplicationEventPublisher events, Clock clock) {
        this.tickets = tickets;
        this.comments = comments;
        this.history = history;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public TicketDetails create(CreateTicketRequest request, String username) {
        AppUser me = users.byUsername(username);
        Instant now = clock.instant();
        Ticket ticket = tickets.save(new Ticket(request.title(), request.description(), request.priority(), me, now));
        record(ticket, me, Type.CREATED, null, request.priority().name(), now);
        events.publishEvent(new TicketChangedEvent(ticket, me, Type.CREATED, "neu angelegt", false));
        tickets.flush();
        return details(ticket, me);
    }

    /** Meldende sehen nur ihre eigenen Tickets, der Support alle. Sortiert nach Frist, Dringendes zuerst. */
    @Transactional(readOnly = true)
    public PageResponse<TicketSummary> list(String username, TicketStatus status, Priority priority,
                                            boolean assignedToMe, int page, int size) {
        AppUser me = users.byUsername(username);
        Specification<Ticket> filter = Specification.allOf(
                TicketFilters.status(status),
                TicketFilters.priority(priority),
                TicketFilters.reporter(me.getRole().isStaff() ? null : me),
                TicketFilters.assignee(assignedToMe ? me : null));
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by("dueAt").ascending().and(Sort.by("id")));
        Instant now = clock.instant();
        return PageResponse.of(tickets.findAll(filter, pageRequest).map(t -> TicketSummary.of(t, now)));
    }

    @Transactional(readOnly = true)
    public TicketDetails get(Long id, String username) {
        AppUser me = users.byUsername(username);
        return details(visibleTicket(id, me), me);
    }

    @Transactional
    public TicketDetails changeStatus(Long id, StatusChangeRequest request, String username) {
        AppUser me = users.byUsername(username);
        Ticket ticket = visibleTicket(id, me);
        checkVersion(ticket, request.version());

        TicketStatus from = ticket.getStatus();
        TicketStatus to = request.status();
        TicketWorkflow.check(from, to, me.getRole());
        boolean hasComment = request.comment() != null && !request.comment().isBlank();
        if (to == TicketStatus.RESOLVED && !hasComment) {
            throw ApiException.badRequest("Beim Lösen bitte die Lösung als Kommentar angeben");
        }

        Instant now = clock.instant();
        if (to == TicketStatus.IN_PROGRESS && ticket.getAssignee() == null && me.getRole().isStaff()) {
            ticket.assign(me, now); // wer ein Ticket übernimmt, bearbeitet es
            record(ticket, me, Type.ASSIGNED, null, me.getUsername(), now);
        }
        ticket.changeStatus(to, now);
        record(ticket, me, Type.STATUS_CHANGED, from.name(), to.name(), now);
        if (hasComment) {
            addComment(ticket, me, request.comment(), false, now);
        }
        events.publishEvent(new TicketChangedEvent(ticket, me, Type.STATUS_CHANGED, "Status %s → %s".formatted(from, to), false));
        tickets.flush(); // erhöht die Version, damit die Antwort die aktuelle enthält
        return details(ticket, me);
    }

    @Transactional
    public TicketDetails assign(Long id, AssignRequest request, String username) {
        AppUser me = requireStaff(username);
        Ticket ticket = visibleTicket(id, me);
        checkVersion(ticket, request.version());
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw ApiException.conflict("Geschlossene Tickets können nicht zugewiesen werden");
        }
        AppUser agent = users.byUsername(request.username());
        if (!agent.getRole().isStaff()) {
            throw ApiException.badRequest("Tickets können nur Support-Mitarbeitenden zugewiesen werden");
        }
        Instant now = clock.instant();
        String previous = TicketDtos.username(ticket.getAssignee());
        ticket.assign(agent, now);
        record(ticket, me, Type.ASSIGNED, previous, agent.getUsername(), now);
        events.publishEvent(new TicketChangedEvent(ticket, me, Type.ASSIGNED, "zugewiesen an " + agent.getDisplayName(), true));
        tickets.flush();
        return details(ticket, me);
    }

    @Transactional
    public TicketDetails changePriority(Long id, PriorityChangeRequest request, String username) {
        AppUser me = requireStaff(username);
        Ticket ticket = visibleTicket(id, me);
        checkVersion(ticket, request.version());
        Priority from = ticket.getPriority();
        if (from == request.priority()) {
            return details(ticket, me);
        }
        Instant now = clock.instant();
        ticket.changePriority(request.priority(), now);
        record(ticket, me, Type.PRIORITY_CHANGED, from.name(), request.priority().name(), now);
        events.publishEvent(new TicketChangedEvent(ticket, me, Type.PRIORITY_CHANGED,
                "Priorität %s → %s".formatted(from, request.priority()), false));
        tickets.flush();
        return details(ticket, me);
    }

    /** Kommentare ändern das Ticket selbst nicht und erhöhen daher auch nicht dessen Version. */
    @Transactional
    public CommentResponse comment(Long id, CommentRequest request, String username) {
        AppUser me = users.byUsername(username);
        Ticket ticket = visibleTicket(id, me);
        if (request.internal() && !me.getRole().isStaff()) {
            throw ApiException.forbidden("Nur der Support kann interne Notizen schreiben");
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw ApiException.conflict("Geschlossene Tickets können nicht mehr kommentiert werden");
        }
        Comment comment = addComment(ticket, me, request.text(), request.internal(), clock.instant());
        events.publishEvent(new TicketChangedEvent(ticket, me, Type.COMMENTED,
                "neuer Kommentar von " + me.getDisplayName(), request.internal()));
        return CommentResponse.of(comment);
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> history(Long id, String username) {
        AppUser me = users.byUsername(username);
        Ticket ticket = visibleTicket(id, me);
        return history.findByTicketOrderByAtAscIdAsc(ticket).stream()
                .filter(h -> me.getRole().isStaff() || !INTERNAL.equals(h.getToValue()))
                .map(HistoryResponse::of)
                .toList();
    }

    /** 404 statt 403 bei fremden Tickets: sonst ließe sich herausfinden, welche IDs existieren. */
    private Ticket visibleTicket(Long id, AppUser me) {
        Ticket ticket = tickets.findById(id).orElseThrow(() -> ApiException.notFound("Ticket " + id));
        if (!me.getRole().isStaff() && !ticket.getReporter().getId().equals(me.getId())) {
            throw ApiException.notFound("Ticket " + id);
        }
        return ticket;
    }

    private AppUser requireStaff(String username) {
        AppUser me = users.byUsername(username);
        if (!me.getRole().isStaff()) {
            throw ApiException.forbidden("Nur der Support darf das");
        }
        return me;
    }

    private static void checkVersion(Ticket ticket, Long expected) {
        if (expected != null && expected != ticket.getVersion()) {
            throw ApiException.conflict("Das Ticket wurde inzwischen geändert (Version %d statt %d). Bitte neu laden."
                    .formatted(ticket.getVersion(), expected));
        }
    }

    private Comment addComment(Ticket ticket, AppUser author, String text, boolean internal, Instant now) {
        Comment comment = comments.save(new Comment(ticket, author, text.strip(), internal, now));
        record(ticket, author, Type.COMMENTED, null, internal ? INTERNAL : null, now);
        return comment;
    }

    private void record(Ticket ticket, AppUser actor, Type type, String from, String to, Instant at) {
        history.save(new HistoryEntry(ticket, actor, type, from, to, at));
    }

    private TicketDetails details(Ticket t, AppUser me) {
        boolean staff = me.getRole().isStaff();
        List<CommentResponse> visibleComments = comments.findByTicketOrderByCreatedAtAscIdAsc(t).stream()
                .filter(c -> staff || !c.isInternal())
                .map(CommentResponse::of)
                .toList();
        return new TicketDetails(t.getId(), t.getTitle(), t.getDescription(), t.getStatus(), t.getPriority(),
                t.getReporter().getUsername(), TicketDtos.username(t.getAssignee()), t.getCreatedAt(), t.getUpdatedAt(),
                t.getDueAt(), t.getResolvedAt(), t.isOverdue(clock.instant()), t.getVersion(),
                TicketWorkflow.nextFor(t.getStatus(), me.getRole()), visibleComments);
    }
}
