-- A board of one tenant; the test contracts scope-reference.yaml and scope-reference-value.yaml use it.
CREATE TABLE board (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    cover_note_id UUID,
    tenant_id TEXT NOT NULL
);
-- A note belongs to one tenant and one user; both columns are scope columns (P6.11).
CREATE TABLE note (
    id UUID PRIMARY KEY,
    text VARCHAR(200) NOT NULL,
    tenant_id TEXT NOT NULL,
    owner_id TEXT NOT NULL,
    board_id UUID REFERENCES board(id)
);
