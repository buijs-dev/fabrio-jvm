CREATE TABLE participant (
    id         BIGINT PRIMARY KEY,
    public_id  UUID NOT NULL UNIQUE,
    rank       INTEGER NOT NULL,
    raw        JSONB,
    loadout_id BIGINT REFERENCES loadout (id)
);
