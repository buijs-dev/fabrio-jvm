-- The decision of a draft shipment (lifecycle L5.6, L5.7): the state `rejected`, when it was
-- approved, and why it was rejected.
ALTER TABLE shipment DROP CONSTRAINT shipment_state_check;
ALTER TABLE shipment ADD CONSTRAINT shipment_state_check CHECK (state IN ('draft', 'approved', 'rejected'));
ALTER TABLE shipment ADD COLUMN approved_at TIMESTAMPTZ;
ALTER TABLE shipment ADD COLUMN rejection_reason VARCHAR(280);
