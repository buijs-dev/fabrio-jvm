CREATE TABLE club (
    storage_id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    name TEXT NOT NULL UNIQUE,
    founded DATE NOT NULL,
    version INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE person (id UUID PRIMARY KEY, name TEXT NOT NULL);
CREATE TABLE club_member (
    club_id BIGINT NOT NULL REFERENCES club(storage_id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES person(id),
    position INTEGER NOT NULL,
    PRIMARY KEY (club_id, person_id)
);
CREATE TABLE club_line (
    id UUID PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES club(storage_id) ON DELETE CASCADE,
    label TEXT NOT NULL,
    position INTEGER NOT NULL
);
