-- Who deleted a carrier: the verified actor a soft delete records and a restore clears
-- (persistence P6.10, storage.Carrier.delete.by).
ALTER TABLE carrier ADD COLUMN deleted_by TEXT;
