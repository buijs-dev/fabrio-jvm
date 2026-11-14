CREATE TABLE thing (
    storage_id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    colour VARCHAR(40) NOT NULL,
    note TEXT,
    version INTEGER NOT NULL DEFAULT 1
);
