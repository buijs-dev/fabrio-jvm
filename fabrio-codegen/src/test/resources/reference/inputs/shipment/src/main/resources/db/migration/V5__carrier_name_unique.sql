-- A carrier is addressed by its name (getCarrierByName), so the name is a unique key
-- (persistence P3.6, storage.Carrier.unique).
ALTER TABLE carrier ADD CONSTRAINT carrier_name_key UNIQUE (name);
