CREATE TABLE membership (
    public_id UUID PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES club(storage_id),
    person_id UUID NOT NULL REFERENCES person(id),
    UNIQUE (club_id, person_id)
);
