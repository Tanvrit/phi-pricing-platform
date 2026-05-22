-- V2: Audit support + real OTP storage.
--
-- - rate_meta : tracks which version of the actuarial rate table is currently active.
--               Stamped into every QuoteResult.rateTableVersion for audit reproducibility.
-- - otp_records : server-side OTP store with SHA-256 hashed codes, TTL, and attempt counter.
--                 Replaces the pre-Foundation-Pack mock OTPs that accepted any 4/6-digit input.
-- - quotes.updated_at : last-modified marker for the (rare) case a quote record is amended.

CREATE TABLE IF NOT EXISTS rate_meta (
    version VARCHAR(100) PRIMARY KEY,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

CREATE TABLE IF NOT EXISTS otp_records (
    mobile VARCHAR(20) PRIMARY KEY,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    purpose VARCHAR(20) NOT NULL DEFAULT 'login'  -- 'login' | 'kyc'
);
CREATE INDEX IF NOT EXISTS idx_otp_expires_at ON otp_records (expires_at);

-- Audit-friendly column on quotes (kept nullable so prior rows don't need backfill).
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
