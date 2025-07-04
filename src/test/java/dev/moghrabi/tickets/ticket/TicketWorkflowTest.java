package dev.moghrabi.tickets.ticket;

import static dev.moghrabi.tickets.ticket.TicketStatus.CLOSED;
import static dev.moghrabi.tickets.ticket.TicketStatus.IN_PROGRESS;
import static dev.moghrabi.tickets.ticket.TicketStatus.OPEN;
import static dev.moghrabi.tickets.ticket.TicketStatus.RESOLVED;
import static dev.moghrabi.tickets.ticket.TicketStatus.WAITING;
import static dev.moghrabi.tickets.user.Role.ADMIN;
import static dev.moghrabi.tickets.user.Role.AGENT;
import static dev.moghrabi.tickets.user.Role.REPORTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.moghrabi.tickets.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Reiner Unit-Test ohne Spring und Datenbank. */
class TicketWorkflowTest {

    @Test
    void agentCarriesTicketThroughTheNormalPath() {
        assertThatCode(() -> {
            TicketWorkflow.check(OPEN, IN_PROGRESS, AGENT);
            TicketWorkflow.check(IN_PROGRESS, WAITING, AGENT);
            TicketWorkflow.check(WAITING, IN_PROGRESS, AGENT);
            TicketWorkflow.check(IN_PROGRESS, RESOLVED, AGENT);
        }).doesNotThrowAnyException();
    }

    @Test
    void reporterConfirmsOrReopens() {
        assertThatCode(() -> TicketWorkflow.check(RESOLVED, CLOSED, REPORTER)).doesNotThrowAnyException();
        assertThatCode(() -> TicketWorkflow.check(RESOLVED, IN_PROGRESS, REPORTER)).doesNotThrowAnyException();
        assertThatCode(() -> TicketWorkflow.check(WAITING, IN_PROGRESS, REPORTER)).doesNotThrowAnyException();
    }

    @Test
    void missingTransitionIsConflict() {
        assertThatThrownBy(() -> TicketWorkflow.check(OPEN, RESOLVED, ADMIN))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> TicketWorkflow.check(CLOSED, OPEN, ADMIN)).isInstanceOf(ApiException.class);
    }

    @Test
    void wrongRoleIsForbidden() {
        assertThatThrownBy(() -> TicketWorkflow.check(OPEN, IN_PROGRESS, REPORTER))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> TicketWorkflow.check(OPEN, CLOSED, AGENT))
                .satisfies(e -> assertThat(((ApiException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void nextStatusesDependOnRole() {
        assertThat(TicketWorkflow.nextFor(OPEN, REPORTER)).isEmpty();
        assertThat(TicketWorkflow.nextFor(OPEN, AGENT)).containsExactly(IN_PROGRESS);
        assertThat(TicketWorkflow.nextFor(OPEN, ADMIN)).containsExactly(IN_PROGRESS, CLOSED);
        assertThat(TicketWorkflow.nextFor(RESOLVED, REPORTER)).containsExactly(IN_PROGRESS, CLOSED);
        assertThat(TicketWorkflow.nextFor(CLOSED, ADMIN)).isEmpty();
    }
}
