CREATE TABLE recipe (
    storage_id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE
);
CREATE TABLE step (
    id UUID PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipe(storage_id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    position INTEGER NOT NULL,
    label TEXT NOT NULL
);
CREATE TABLE comment (
    id UUID PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipe(storage_id) ON DELETE CASCADE,
    posted_at TIMESTAMPTZ NOT NULL,
    text TEXT NOT NULL,
    pinned BOOLEAN NOT NULL,
    likes INTEGER NOT NULL,
    position BIGINT NOT NULL,
    slot INTEGER
);
CREATE TABLE reviewer (id UUID PRIMARY KEY, name TEXT NOT NULL);
CREATE TABLE recipe_reviewer (
    recipe_id BIGINT NOT NULL REFERENCES recipe(storage_id) ON DELETE CASCADE,
    reviewer_id UUID NOT NULL REFERENCES reviewer(id),
    position INTEGER NOT NULL,
    rank INTEGER NOT NULL,
    PRIMARY KEY (recipe_id, reviewer_id)
);
CREATE TABLE label (id UUID PRIMARY KEY, name TEXT NOT NULL);
CREATE TABLE recipe_label (
    recipe_id BIGINT NOT NULL REFERENCES recipe(storage_id) ON DELETE CASCADE,
    label_id UUID NOT NULL REFERENCES label(id),
    PRIMARY KEY (recipe_id, label_id)
);
CREATE TABLE photo (
    id UUID PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipe(storage_id),
    size BIGINT NOT NULL,
    taken_on DATE
);
CREATE TABLE pantry (
    storage_id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE
);
CREATE TABLE pantry_photo (
    pantry_id BIGINT NOT NULL REFERENCES pantry(storage_id) ON DELETE CASCADE,
    photo_id UUID NOT NULL REFERENCES photo(id),
    position INTEGER NOT NULL,
    PRIMARY KEY (pantry_id, photo_id)
);
