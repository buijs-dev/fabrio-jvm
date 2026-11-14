CREATE TABLE orders (
    storage_id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    total INTEGER NOT NULL,
    note TEXT,
    version INTEGER NOT NULL DEFAULT 1
);
