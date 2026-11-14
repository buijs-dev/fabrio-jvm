CREATE TABLE loadout (
    id        BIGINT PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    label     VARCHAR(100) NOT NULL
);
