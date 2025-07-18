package dev.moghrabi.tickets.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.moghrabi.tickets.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Durchläuft die API mit den Demo-Benutzern (anna, dora: REPORTER, ben, clara: AGENT, admin). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TicketApiTest {

    @Autowired
    MockMvc mvc;

    static RequestPostProcessor as(String user) {
        return httpBasic(user, "demo123");
    }

    long create(String user, String priority) throws Exception {
        String json = """
                {"title": "Problem %s", "description": "Geht nicht.", "priority": "%s"}
                """.formatted(UUID.randomUUID(), priority);
        String body = mvc.perform(post("/api/tickets").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    ResultActions setStatus(long id, String user, String status, String comment) throws Exception {
        String json = comment == null
                ? "{\"status\": \"%s\"}".formatted(status)
                : "{\"status\": \"%s\", \"comment\": \"%s\"}".formatted(status, comment);
        return mvc.perform(patch("/api/tickets/{id}/status", id).with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions patchJson(String path, long id, String user, String json) throws Exception {
        return mvc.perform(patch(path, id).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tickets").with(httpBasic("anna", "falsch"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").with(as("anna")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REPORTER"));
    }

    @Test
    void newTicketGetsDeadlineFromPriority() throws Exception {
        long id = create("anna", "HIGH");
        String body = mvc.perform(get("/api/tickets/{id}", id).with(as("anna")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reporter").value("anna"))
                .andExpect(jsonPath("$.nextStatuses.length()").value(0))
                .andReturn().getResponse().getContentAsString();

        Instant created = Instant.parse(JsonPath.read(body, "$.createdAt"));
        Instant due = Instant.parse(JsonPath.read(body, "$.dueAt"));
        assertThat(Duration.between(created, due)).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void fullWorkflowWritesHistoryAndNotifies() throws Exception {
        long id = create("anna", "MEDIUM");

        setStatus(id, "ben", "IN_PROGRESS", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value("ben"));
        setStatus(id, "ben", "RESOLVED", "Toner getauscht")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").value(notNullValue()))
                .andExpect(jsonPath("$.comments[0].text").value("Toner getauscht"));
        setStatus(id, "anna", "CLOSED", null).andExpect(status().isOk());

        mvc.perform(get("/api/tickets/{id}/history", id).with(as("admin")))
                .andExpect(jsonPath("$[*].type").value(contains(
                        "CREATED", "ASSIGNED", "STATUS_CHANGED", "STATUS_CHANGED", "COMMENTED", "STATUS_CHANGED")));

        // anna: zwei Statusänderungen durch ben. ben: der Abschluss durch anna.
        mvc.perform(get("/api/notifications").with(as("anna")))
                .andExpect(jsonPath("$[?(@.ticketId == %d)]".formatted(id)).value(org.hamcrest.Matchers.hasSize(2)));
        mvc.perform(get("/api/notifications").with(as("ben")))
                .andExpect(jsonPath("$[?(@.ticketId == %d)]".formatted(id)).value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void workflowRulesAreEnforced() throws Exception {
        long id = create("anna", "LOW");

        setStatus(id, "anna", "IN_PROGRESS", null).andExpect(status().isForbidden());
        setStatus(id, "ben", "RESOLVED", "x").andExpect(status().isConflict());
        setStatus(id, "ben", "IN_PROGRESS", null).andExpect(status().isOk());
        setStatus(id, "ben", "RESOLVED", null).andExpect(status().isBadRequest());
        setStatus(id, "ben", "CLOSED", null).andExpect(status().isConflict());
    }

    @Test
    void reportersOnlySeeTheirOwnTickets() throws Exception {
        long doras = create("dora", "LOW");
        create("anna", "LOW");

        mvc.perform(get("/api/tickets/{id}", doras).with(as("anna"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/tickets").with(as("anna")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].reporter").value(everyItem(is("anna"))));
        mvc.perform(get("/api/tickets/{id}", doras).with(as("ben"))).andExpect(status().isOk());
    }

    @Test
    void internalNotesAreHiddenFromReporter() throws Exception {
        long id = create("anna", "MEDIUM");
        String internal = "{\"text\": \"Lieferant anrufen\", \"internal\": true}";
        String visible = "{\"text\": \"Wir schauen uns das an\", \"internal\": false}";

        mvc.perform(post("/api/tickets/{id}/comments", id).with(as("ben")).contentType(MediaType.APPLICATION_JSON).content(internal))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/tickets/{id}/comments", id).with(as("ben")).contentType(MediaType.APPLICATION_JSON).content(visible))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/tickets/{id}/comments", id).with(as("anna")).contentType(MediaType.APPLICATION_JSON).content(internal))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/tickets/{id}", id).with(as("anna")))
                .andExpect(jsonPath("$.comments.length()").value(1))
                .andExpect(jsonPath("$.comments[0].text").value("Wir schauen uns das an"));
        mvc.perform(get("/api/tickets/{id}", id).with(as("ben")))
                .andExpect(jsonPath("$.comments.length()").value(2));
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        long id = create("anna", "MEDIUM");

        patchJson("/api/tickets/{id}/status", id, "ben", "{\"status\": \"IN_PROGRESS\", \"version\": 0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // clara hat noch Version 0 geladen
        patchJson("/api/tickets/{id}/priority", id, "clara", "{\"priority\": \"CRITICAL\", \"version\": 0}")
                .andExpect(status().isConflict());
        String body = patchJson("/api/tickets/{id}/priority", id, "clara", "{\"priority\": \"CRITICAL\", \"version\": 1}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Instant created = Instant.parse(JsonPath.read(body, "$.createdAt"));
        Instant due = Instant.parse(JsonPath.read(body, "$.dueAt"));
        assertThat(Duration.between(created, due)).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void ticketsCanOnlyBeAssignedToStaff() throws Exception {
        long id = create("anna", "HIGH");

        patchJson("/api/tickets/{id}/assignee", id, "ben", "{\"username\": \"anna\"}").andExpect(status().isBadRequest());
        patchJson("/api/tickets/{id}/assignee", id, "anna", "{\"username\": \"ben\"}").andExpect(status().isForbidden());
        patchJson("/api/tickets/{id}/assignee", id, "ben", "{\"username\": \"clara\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value("clara"));

        mvc.perform(get("/api/notifications").param("unreadOnly", "true").with(as("clara")))
                .andExpect(jsonPath("$[?(@.ticketId == %d)]".formatted(id)).value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void listCanBeFilteredByStatusAndAssignee() throws Exception {
        long mine = create("anna", "LOW");
        create("anna", "LOW");
        setStatus(mine, "clara", "IN_PROGRESS", null).andExpect(status().isOk());

        mvc.perform(get("/api/tickets").param("status", "IN_PROGRESS").param("assignedToMe", "true").with(as("clara")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status").value(everyItem(is("IN_PROGRESS"))))
                .andExpect(jsonPath("$.content[*].assignee").value(everyItem(is("clara"))))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void adminOnlyEndpointsAreProtected() throws Exception {
        String user = "{\"username\": \"neu\", \"displayName\": \"Neu\", \"password\": \"geheim1234\", \"role\": \"AGENT\"}";
        mvc.perform(post("/api/users").with(as("ben")).contentType(MediaType.APPLICATION_JSON).content(user))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/staff").with(as("anna"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/staff").with(as("ben")))
                .andExpect(jsonPath("$[*].role").value(everyItem(org.hamcrest.Matchers.anyOf(is("AGENT"), is("ADMIN")))));
    }
}
