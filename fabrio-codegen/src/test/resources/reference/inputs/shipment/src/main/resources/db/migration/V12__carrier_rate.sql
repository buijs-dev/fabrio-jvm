-- A carrier's rate for a zone (scenario S32): carrier and zone are its identity together, neither
-- alone is unique (domain D2.2, persistence P2.2).
CREATE TABLE carrier_rate (
    carrier_id UUID NOT NULL REFERENCES carrier(id),
    zone TEXT NOT NULL,
    amount DECIMAL(10,2) NOT NULL CHECK (amount >= 0),
    PRIMARY KEY (carrier_id, zone)
);
