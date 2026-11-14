-- App-only tables of the P6.11 reference tests (not in rialto-spec's Shipment): a tenant-scoped
-- target, a tenant-scoped owner of it, and an actor-scoped owner of an actor-scoped pin.
CREATE TABLE remark_topic (
    storage_id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    title TEXT NOT NULL,
    tenant_id TEXT NOT NULL
);
CREATE TABLE shipment_remark (
    id UUID PRIMARY KEY,
    text TEXT NOT NULL,
    topic_id BIGINT NOT NULL REFERENCES remark_topic(storage_id),
    tenant_id TEXT NOT NULL
);
CREATE TABLE search_pin (
    id UUID PRIMARY KEY,
    label TEXT NOT NULL,
    tenant_id TEXT NOT NULL,
    owner_id TEXT NOT NULL
);
CREATE TABLE search_alert (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    pin_id UUID NOT NULL REFERENCES search_pin(id),
    tenant_id TEXT NOT NULL,
    owner_id TEXT NOT NULL
);
