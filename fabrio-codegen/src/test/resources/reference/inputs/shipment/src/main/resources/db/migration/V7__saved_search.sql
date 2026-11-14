-- A user's saved shipment list filters (Rialto plan 20b, P6.11): scoped to the tenant and the actor.
CREATE TABLE saved_search (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    filter VARCHAR(1000) NOT NULL,
    tenant_id TEXT NOT NULL,
    owner_id TEXT NOT NULL
);
