-- Reference DDL for fabrio-quarkus-events and fabrio-quarkus-idempotency
-- Outbox, Inbox, and Idempotency Record tables

CREATE TABLE fabrio_event_outbox (
    position         BIGSERIAL     PRIMARY KEY,
    id               UUID          NOT NULL UNIQUE,
    source           VARCHAR(2048) NOT NULL,
    type             VARCHAR(255)  NOT NULL,
    subject          VARCHAR(255)  NOT NULL,
    protocol         VARCHAR(16)   NOT NULL,
    destination      VARCHAR(255)  NOT NULL,
    mode             VARCHAR(16)   NOT NULL,
    dataschema       VARCHAR(2048),
    data             TEXT          NOT NULL,
    tenant           VARCHAR(255),
    time             TIMESTAMPTZ   NOT NULL DEFAULT clock_timestamp(),
    published_at     TIMESTAMPTZ,
    publish_attempts INTEGER       NOT NULL DEFAULT 0,
    last_error       VARCHAR(2048)
);

CREATE INDEX ix_fabrio_event_outbox_pending ON fabrio_event_outbox (position) WHERE published_at IS NULL;
CREATE INDEX ix_fabrio_event_outbox_published_at ON fabrio_event_outbox (published_at);

CREATE OR REPLACE FUNCTION fabrio_event_outbox_committed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE fabrio_event_outbox SET time = clock_timestamp() WHERE position = NEW.position;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER fabrio_event_outbox_committed
    AFTER INSERT ON fabrio_event_outbox
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fabrio_event_outbox_committed();

CREATE TABLE fabrio_event_inbox (
    subscription VARCHAR(255)  NOT NULL,
    source       VARCHAR(2048) NOT NULL,
    id           VARCHAR(255)  NOT NULL,
    type         VARCHAR(255)  NOT NULL,
    handled_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (subscription, source, id)
);

CREATE INDEX ix_fabrio_event_inbox_handled_at ON fabrio_event_inbox (handled_at);

CREATE TABLE idempotency_record (
    scope                 VARCHAR(255)  NOT NULL,
    request_id            UUID          NOT NULL,
    request_method        VARCHAR(16)   NOT NULL,
    request_path          VARCHAR(2048) NOT NULL,
    request_checksum      VARCHAR(64)   NOT NULL,
    response_code         INTEGER,
    response_content_type VARCHAR(255),
    response_body         TEXT,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (scope, request_id)
);

CREATE INDEX ix_idempotency_record_created_at ON idempotency_record (created_at);
