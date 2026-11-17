-- The schema of the Fabrio shipment example: rialto-spec's examples/shipment/schema.sql without
-- what this example leaves out until Fabrio supports it (unions, composite keys).
CREATE TABLE warehouse (
    storage_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE
);
CREATE TABLE customs_declaration (id UUID PRIMARY KEY, contents TEXT NOT NULL);
CREATE TABLE handling_unit (
    id UUID PRIMARY KEY,
    kind TEXT NOT NULL CHECK (kind IN ('pallet', 'container')),
    capacity_kg INTEGER,
    seal TEXT,
    CHECK ((kind = 'pallet' AND capacity_kg >= 1 AND seal IS NULL)
        OR (kind = 'container' AND seal IS NOT NULL AND capacity_kg IS NULL))
);
CREATE TABLE shipment (
    storage_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version >= 1),
    delivery_note VARCHAR(280),
    expected_delivery DATE,
    destination_street VARCHAR(200) NOT NULL,
    destination_city VARCHAR(120) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'draft' CHECK (state IN ('draft', 'approved')),
    warehouse_id BIGINT REFERENCES warehouse(storage_id),
    priority TEXT NOT NULL DEFAULT 'standard',
    recipient JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_by TEXT,
    label TEXT NOT NULL DEFAULT 'shipment-label',
    service_codes TEXT[] NOT NULL,
    tenant_id TEXT NOT NULL,
    handling_unit_id UUID REFERENCES handling_unit(id),
    customs_declaration_id UUID REFERENCES customs_declaration(id) ON DELETE RESTRICT
);
CREATE TABLE shipment_line (
    public_id UUID PRIMARY KEY,
    shipment_id BIGINT NOT NULL REFERENCES shipment(storage_id),
    quantity INTEGER NOT NULL CHECK (quantity >= 1)
);
-- Ownership is a metadata / application policy, not inferred from these FKs.
-- Aggregate operations update Shipment.version whenever its response representation changes.

CREATE TABLE tag (id UUID PRIMARY KEY, name TEXT NOT NULL);

CREATE TABLE shipment_tag (
    shipment_id BIGINT NOT NULL REFERENCES shipment(storage_id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tag(id),
    PRIMARY KEY (shipment_id, tag_id)
);
CREATE TABLE tracking_scan (
    id UUID PRIMARY KEY,
    shipment_id BIGINT NOT NULL REFERENCES shipment(storage_id),
    scanned_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE shipment_package (
    id UUID PRIMARY KEY,
    shipment_id BIGINT NOT NULL REFERENCES shipment(storage_id) ON DELETE CASCADE,
    weight_kg DECIMAL(8,3) NOT NULL CHECK (weight_kg > 0)
);
CREATE TABLE carrier (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    contact_email TEXT,
    deleted_at TIMESTAMPTZ
);
