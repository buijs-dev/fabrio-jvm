-- A later migration: the check reads the schema that results from all migrations (K1.9).
ALTER TABLE thing ADD COLUMN size INTEGER;
ALTER TABLE thing ADD COLUMN shade TEXT;
