-- The display name of a warehouse (U5.11, U2.11: the warehouse link and the warehouse choice).
-- Existing rows get a name from their public id, so the column can be NOT NULL at once.
ALTER TABLE warehouse ADD COLUMN name VARCHAR(120);
UPDATE warehouse SET name = 'Warehouse ' || public_id::text;
ALTER TABLE warehouse ALTER COLUMN name SET NOT NULL;
