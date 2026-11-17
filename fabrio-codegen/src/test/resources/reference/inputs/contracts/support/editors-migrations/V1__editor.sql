CREATE TABLE editor (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by TEXT
);
