package dev.moghrabi.tickets.ticket;

import java.time.Instant;

import dev.moghrabi.tickets.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private AppUser reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    private long version;

    protected Ticket() {
        // für JPA
    }

    public Ticket(String title, String description, Priority priority, AppUser reporter, Instant now) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.reporter = reporter;
        this.status = TicketStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
        this.dueAt = now.plus(priority.sla());
    }

    void changeStatus(TicketStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
        if (next == TicketStatus.RESOLVED) {
            resolvedAt = now;
        } else if (!next.isDone()) {
            resolvedAt = null; // wieder geöffnet
        }
    }

    void assign(AppUser agent, Instant now) {
        this.assignee = agent;
        this.updatedAt = now;
    }

    /** Neue Priorität, die Frist wird ab Erstellung neu berechnet. */
    void changePriority(Priority next, Instant now) {
        this.priority = next;
        this.dueAt = createdAt.plus(next.sla());
        this.updatedAt = now;
    }

    public boolean isOverdue(Instant now) {
        return !status.isDone() && now.isAfter(dueAt);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Priority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public AppUser getReporter() {
        return reporter;
    }

    public AppUser getAssignee() {
        return assignee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public long getVersion() {
        return version;
    }
}
