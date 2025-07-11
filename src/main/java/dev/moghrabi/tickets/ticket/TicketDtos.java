package dev.moghrabi.tickets.ticket;

import java.time.Instant;
import java.util.List;

import dev.moghrabi.tickets.user.AppUser;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

public final class TicketDtos {

    private TicketDtos() {
    }

    public record CreateTicketRequest(
            @Schema(example = "Drucker im 2. Stock druckt nicht") @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10_000) String description,
            @NotNull Priority priority) {
    }

    /** version: optional, die zuletzt gelesene Version. Weicht sie ab, gibt es 409 statt eines stillen Überschreibens. */
    public record StatusChangeRequest(@NotNull TicketStatus status, @Size(max = 10_000) String comment, Long version) {
    }

    public record AssignRequest(@Schema(example = "ben") @NotBlank String username, Long version) {
    }

    public record PriorityChangeRequest(@NotNull Priority priority, Long version) {
    }

    public record CommentRequest(@NotBlank @Size(max = 10_000) String text, boolean internal) {
    }

    public record TicketSummary(
            Long id,
            String title,
            TicketStatus status,
            Priority priority,
            String reporter,
            String assignee,
            Instant createdAt,
            Instant dueAt,
            boolean overdue,
            long version) {

        static TicketSummary of(Ticket t, Instant now) {
            return new TicketSummary(t.getId(), t.getTitle(), t.getStatus(), t.getPriority(),
                    t.getReporter().getUsername(), username(t.getAssignee()), t.getCreatedAt(), t.getDueAt(),
                    t.isOverdue(now), t.getVersion());
        }
    }

    public record TicketDetails(
            Long id,
            String title,
            String description,
            TicketStatus status,
            Priority priority,
            String reporter,
            String assignee,
            Instant createdAt,
            Instant updatedAt,
            Instant dueAt,
            Instant resolvedAt,
            boolean overdue,
            long version,
            List<TicketStatus> nextStatuses,
            List<CommentResponse> comments) {
    }

    public record CommentResponse(Long id, String author, String text, boolean internal, Instant createdAt) {

        static CommentResponse of(Comment c) {
            return new CommentResponse(c.getId(), c.getAuthor().getUsername(), c.getText(), c.isInternal(), c.getCreatedAt());
        }
    }

    public record HistoryResponse(String actor, HistoryEntry.Type type, String from, String to, Instant at) {

        static HistoryResponse of(HistoryEntry h) {
            return new HistoryResponse(h.getActor().getUsername(), h.getType(), h.getFromValue(), h.getToValue(), h.getAt());
        }
    }

    /** Eigene Seitenstruktur statt Spring Datas PageImpl, dessen JSON-Form nicht stabil ist. */
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                    page.getTotalPages());
        }
    }

    static String username(AppUser user) {
        return user == null ? null : user.getUsername();
    }
}
