-- The order of lines and packages, as the client sent them (persistence P4.13, P4.14:
-- `order: position`). No property maps to the column. Existing rows get their order by storage
-- key, so the column can be NOT NULL at once.
ALTER TABLE shipment_line ADD COLUMN position INTEGER;
UPDATE shipment_line SET position = ordered.position
FROM (
    SELECT public_id, row_number() OVER (PARTITION BY shipment_id ORDER BY public_id) - 1 AS position
    FROM shipment_line
) ordered
WHERE shipment_line.public_id = ordered.public_id;
ALTER TABLE shipment_line ALTER COLUMN position SET NOT NULL;

ALTER TABLE shipment_package ADD COLUMN position INTEGER;
UPDATE shipment_package SET position = ordered.position
FROM (
    SELECT id, row_number() OVER (PARTITION BY shipment_id ORDER BY id) - 1 AS position
    FROM shipment_package
) ordered
WHERE shipment_package.id = ordered.id;
ALTER TABLE shipment_package ALTER COLUMN position SET NOT NULL;
