-- The proof of delivery (persistence P7): the content is in the blob store `proofs`; the row keeps
-- its key and metadata.
CREATE TABLE delivery_proof (
    shipment_id UUID PRIMARY KEY,
    file_key VARCHAR(200) NOT NULL,
    file_media_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    tenant_id TEXT NOT NULL
);

-- The table of fabrio-persistence-postgres's PostgresBlobStore, for a store kept in the database.
CREATE TABLE fabrio_blob (
    store VARCHAR(100) NOT NULL,
    key VARCHAR(512) NOT NULL,
    content BYTEA NOT NULL,
    PRIMARY KEY (store, key)
);
