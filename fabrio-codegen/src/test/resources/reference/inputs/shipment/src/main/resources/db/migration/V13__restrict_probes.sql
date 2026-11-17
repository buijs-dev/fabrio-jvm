-- App-only tables of the restrict reference tests (not in rialto-spec's Shipment): a sheet whose
-- entries and zone links restrict deleting it (persistence P4.9, P4.12).
CREATE TABLE rate_sheet (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL
);
CREATE TABLE rate_sheet_entry (
    id UUID PRIMARY KEY,
    label TEXT NOT NULL,
    sheet_id UUID NOT NULL REFERENCES rate_sheet(id)
);
CREATE TABLE rate_zone (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL
);
CREATE TABLE rate_sheet_zone (
    sheet_id UUID NOT NULL REFERENCES rate_sheet(id),
    zone_id UUID NOT NULL REFERENCES rate_zone(id),
    PRIMARY KEY (sheet_id, zone_id)
);
