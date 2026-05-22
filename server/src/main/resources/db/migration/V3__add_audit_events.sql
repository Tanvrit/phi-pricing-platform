-- V3: Governance + observability infrastructure (Phase 2).
--
-- audit_event       : append-only, hash-chained ledger of every state-changing action.
--                     Each row's this_hash = SHA-256(prev_hash || canonical_json(event)).
--                     The genesis row uses prev_hash = 'GENESIS'. Verifying the chain
--                     walks rows in id-order and confirms each link.
-- idempotency_key   : POST-route deduplication store. If a client retries with the same
--                     Idempotency-Key header within 24h, the prior response is replayed
--                     verbatim instead of re-processing. Prevents duplicate quote saves,
--                     duplicate proposals, duplicate rate imports.

CREATE TABLE IF NOT EXISTS audit_event (
    id              BIGSERIAL PRIMARY KEY,
    event_at        TIMESTAMP NOT NULL,
    actor_subject   VARCHAR(200),
    actor_role      VARCHAR(50),
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(100) NOT NULL,
    resource_id     VARCHAR(200),
    payload_json    TEXT,
    request_id      VARCHAR(100),
    prev_hash       VARCHAR(64),
    this_hash       VARCHAR(64) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_event_resource    ON audit_event (resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_event_at    ON audit_event (event_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_actor       ON audit_event (actor_subject);

CREATE TABLE IF NOT EXISTS idempotency_key (
    key             VARCHAR(100) PRIMARY KEY,
    route           VARCHAR(200) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_idempotency_created_at ON idempotency_key (created_at);
