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

/** Unveränderlicher Verlaufseintrag: wer hat wann was geändert. */
@Entity
@Table(name = "ticket_history")
public class HistoryEntry {

    public enum Type {
        CREATED,
        STATUS_CHANGED,
        ASSIGNED,
        PRIORITY_CHANGED,
        COMMENTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(name = "from_value", length = 100)
    private String fromValue;

    @Column(name = "to_value", length = 100)
    private String toValue;

    @Column(nullable = false)
    private Instant at;

    protected HistoryEntry() {
        // für JPA
    }

    public HistoryEntry(Ticket ticket, AppUser actor, Type type, String fromValue, String toValue, Instant at) {
        this.ticket = ticket;
        this.actor = actor;
        this.type = type;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.at = at;
    }

    public Long getId() {
        return id;
    }

    public AppUser getActor() {
        return actor;
    }

    public Type getType() {
        return type;
    }

    public String getFromValue() {
        return fromValue;
    }

    public String getToValue() {
        return toValue;
    }

    public Instant getAt() {
        return at;
    }
}
