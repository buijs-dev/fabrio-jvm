CREATE TABLE ghost_player (
    storage_id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    name TEXT NOT NULL
);
CREATE TABLE participant (
    storage_id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    ghost_player_id BIGINT REFERENCES ghost_player(storage_id)
);
CREATE TABLE participant_change (
    id UUID PRIMARY KEY,
    participant_id BIGINT NOT NULL REFERENCES participant(storage_id),
    note TEXT NOT NULL
);
CREATE TABLE claim_request (
    storage_id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE
);
CREATE TABLE claim_edge (
    id UUID PRIMARY KEY,
    created_via_claim_request_id BIGINT REFERENCES claim_request(storage_id)
);
CREATE TABLE app_group (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    member_count INTEGER NOT NULL
);
CREATE TABLE member (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL
);
CREATE TABLE group_member (
    group_id UUID NOT NULL REFERENCES app_group(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES member(id),
    PRIMARY KEY (group_id, member_id)
);
CREATE TABLE group_note (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES app_group(id) ON DELETE CASCADE,
    text TEXT NOT NULL
);
