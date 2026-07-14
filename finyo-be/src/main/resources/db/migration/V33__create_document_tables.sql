-- Catalogue for tax documents discovered in a cloud folder (SharePoint/OneDrive).
--
-- The files themselves stay in the cloud: the drive is the system of record and
-- is already backed up there. We only keep metadata, the extraction result and a
-- reference, so the database stays small and tax documents are not copied onto
-- the application server.

-- Maps a cloud folder to a finyo user. The sync job runs without a request and
-- therefore without a SecurityContext, so it cannot derive the user from a JWT —
-- the owning user_id has to be persisted here.
CREATE TABLE document_source (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    drive_id VARCHAR(255) NOT NULL,
    root_folder_path VARCHAR(1024) NOT NULL,
    -- Opaque Graph cursor; only ever stored once a sync ran to completion, since
    -- the deltaLink is handed out on the last page only.
    delta_link TEXT,
    -- Lease against concurrent runs; stale leases expire by wall clock.
    sync_started_at TIMESTAMPTZ,
    last_sync_at TIMESTAMPTZ,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_document_source_user_drive_folder UNIQUE (user_id, drive_id, root_folder_path)
);
CREATE INDEX idx_document_source_user_id ON document_source(user_id);

CREATE TABLE document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    source_id UUID NOT NULL REFERENCES document_source(id) ON DELETE CASCADE,
    -- Natural key of a remote file. Item IDs are unique per drive, not globally,
    -- so the source is part of the key.
    source_item_id VARCHAR(255) NOT NULL,
    source_path VARCHAR(1024) NOT NULL,
    -- cTag changes only when the *content* changes (eTag also changes on metadata
    -- edits). Unchanged cTag means: no download, no re-parse.
    source_ctag VARCHAR(255),
    -- Soft delete: a document that already fed a value into a tax year stays as
    -- the audit trail for where that number came from. Deleting the cloud file
    -- never rolls back an applied value.
    source_deleted_at TIMESTAMPTZ,
    filename VARCHAR(255) NOT NULL,
    size BIGINT,
    detected_type VARCHAR(30),
    confidence DECIMAL(5,4),
    detected_tax_year INT,
    folder_tax_year INT,
    folder_type VARCHAR(30),
    -- Serialized ExtractedField list (the taxYearMapping). No use case filters by
    -- field name, so a JSON column beats a child table and a join.
    extracted_fields TEXT,
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    -- Transient failures (busy parse slots, Graph 429/5xx) are retried; only after
    -- repeated attempts does a document go to FAILED.
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_document_source_item UNIQUE (source_id, source_item_id)
);
CREATE INDEX idx_document_user_id ON document(user_id);
CREATE INDEX idx_document_user_status ON document(user_id, status);
