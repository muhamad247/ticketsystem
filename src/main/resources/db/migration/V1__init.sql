CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    display_name  VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('REPORTER', 'AGENT', 'ADMIN'))
);

CREATE TABLE ticket (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description TEXT         NOT NULL,
    priority    VARCHAR(20)  NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED')),
    reporter_id BIGINT       NOT NULL REFERENCES app_user (id),
    assignee_id BIGINT REFERENCES app_user (id),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    due_at      TIMESTAMPTZ  NOT NULL,
    resolved_at TIMESTAMPTZ,
    version     BIGINT       NOT NULL DEFAULT 0 -- optimistisches Sperren (JPA @Version)
);

CREATE INDEX idx_ticket_status ON ticket (status);
CREATE INDEX idx_ticket_reporter ON ticket (reporter_id);
CREATE INDEX idx_ticket_assignee ON ticket (assignee_id);

CREATE TABLE ticket_comment (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    author_id  BIGINT      NOT NULL REFERENCES app_user (id),
    text       TEXT        NOT NULL,
    internal   BOOLEAN     NOT NULL, -- interne Notizen sieht nur der Support
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_comment_ticket ON ticket_comment (ticket_id);

CREATE TABLE ticket_history (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    actor_id   BIGINT      NOT NULL REFERENCES app_user (id),
    type       VARCHAR(30) NOT NULL,
    from_value VARCHAR(100),
    to_value   VARCHAR(100),
    at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_history_ticket ON ticket_history (ticket_id);

CREATE TABLE notification (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id BIGINT       NOT NULL REFERENCES app_user (id),
    ticket_id    BIGINT       NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    message      VARCHAR(300) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    read_at      TIMESTAMPTZ
);

CREATE INDEX idx_notification_recipient ON notification (recipient_id, read_at);
