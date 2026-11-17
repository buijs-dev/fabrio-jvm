-- A delivery proof: the content is in a blob store, the row keeps its key and metadata (persistence P7).
CREATE TABLE proof (
    id              BIGINT PRIMARY KEY,
    public_id       UUID NOT NULL UNIQUE,
    file_key        VARCHAR(200) NOT NULL,
    file_media_type VARCHAR(100) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_sha256     CHAR(64) NOT NULL
);
