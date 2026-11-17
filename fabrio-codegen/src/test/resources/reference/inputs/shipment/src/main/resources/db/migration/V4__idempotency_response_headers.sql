-- fabrio-http-postgres stores the headers of a response it replays (ETag, Location), so a
-- repeated update returns the same validator as the first one (lifecycle L3.2).
ALTER TABLE idempotency_record ADD COLUMN response_headers TEXT;
