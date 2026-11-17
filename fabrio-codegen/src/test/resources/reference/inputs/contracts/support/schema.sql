-- Tables the storage test contracts refer to.
CREATE TABLE tag (
    id        BIGINT PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    name      VARCHAR(100) NOT NULL
);

CREATE TABLE thing (
    id        BIGINT PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    name      VARCHAR(100) NOT NULL
);

CREATE TABLE thing_tag (
    thing_id BIGINT NOT NULL REFERENCES thing (id),
    tag_id   BIGINT NOT NULL REFERENCES tag (id),
    PRIMARY KEY (thing_id, tag_id)
);

CREATE TABLE expansion (
    id        BIGINT PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    thing_id  BIGINT NOT NULL REFERENCES thing (id),
    name      VARCHAR(100) NOT NULL
);
