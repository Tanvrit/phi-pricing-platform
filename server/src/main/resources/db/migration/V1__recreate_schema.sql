-- V1: Baseline schema for Rate Calculator.
--
-- IMPORTANT: This migration was previously destructive — it ran DROP TABLE IF EXISTS CASCADE
-- on every startup. That was a footgun for any environment with real data. As of the
-- Foundation Pack the migration is purely additive: CREATE TABLE IF NOT EXISTS only.
-- Future schema changes go in V2__, V3__, ... and never modify this file.
-- See server/src/main/kotlin/com/rate/server/database/DatabaseFactory.kt for the
-- `baselineOnMigrate = true` config that lets existing DBs adopt this versioning.

CREATE TABLE IF NOT EXISTS plans (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    plan_type VARCHAR(50) NOT NULL,
    underwriting_category VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    geography_scope VARCHAR(50) NOT NULL DEFAULT 'DOMESTIC',
    copayment_table VARCHAR(20) NOT NULL DEFAULT 'OMNIBUS',
    description TEXT DEFAULT '',
    available_sum_insureds TEXT NOT NULL,
    available_zones TEXT NOT NULL,
    available_family_types TEXT NOT NULL,
    max_discount_cap DECIMAL(5,4) NOT NULL DEFAULT 0.30,
    rate_table_id VARCHAR(100) DEFAULT '',
    min_age INT NOT NULL DEFAULT 5,
    max_age INT NOT NULL DEFAULT 99,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS base_rates (
    id BIGSERIAL PRIMARY KEY,
    plan_id VARCHAR(100) NOT NULL,
    family_type VARCHAR(20) NOT NULL,
    zone VARCHAR(30) NOT NULL,
    age_band_min INT NOT NULL,
    sum_insured BIGINT NOT NULL,
    annual_premium DECIMAL(14,2) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_base_rates ON base_rates (plan_id, family_type, zone, age_band_min, sum_insured);

CREATE TABLE IF NOT EXISTS cover_rate_lookup (
    id BIGSERIAL PRIMARY KEY,
    cover_id VARCHAR(100) NOT NULL,
    param1_key VARCHAR(200),
    param2_key VARCHAR(200),
    age_band_min INT,
    sum_insured BIGINT,
    plan_id VARCHAR(100),
    rate DECIMAL(16,8) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_cover_rate ON cover_rate_lookup (cover_id, param1_key, param2_key, age_band_min, sum_insured, plan_id);

CREATE TABLE IF NOT EXISTS member_level_rates (
    id BIGSERIAL PRIMARY KEY,
    cover_id VARCHAR(100) NOT NULL,
    age_band_min INT,
    param1_key VARCHAR(200),
    rate DECIMAL(16,8) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_member_rates ON member_level_rates (cover_id, age_band_min, param1_key);

CREATE TABLE IF NOT EXISTS instalment_config (
    id BIGSERIAL PRIMARY KEY,
    policy_tenure VARCHAR(20) NOT NULL,
    payment_tenure VARCHAR(20) NOT NULL,
    payment_mode VARCHAR(30) NOT NULL,
    instalment_count INT NOT NULL,
    UNIQUE (policy_tenure, payment_tenure, payment_mode)
);

CREATE TABLE IF NOT EXISTS discount_rates (
    id VARCHAR(100),
    name VARCHAR(200) NOT NULL,
    param_key VARCHAR(100),
    rate DECIMAL(10,6) NOT NULL
);

CREATE TABLE IF NOT EXISTS cover_availability (
    plan_id VARCHAR(100) NOT NULL,
    cover_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (plan_id, cover_id)
);

CREATE TABLE IF NOT EXISTS quotes (
    id VARCHAR(50) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    plan_id VARCHAR(100) NOT NULL,
    primary_age INT NOT NULL,
    sum_insured BIGINT NOT NULL,
    family_type VARCHAR(20) NOT NULL,
    zone VARCHAR(30) NOT NULL,
    tenure VARCHAR(20) NOT NULL,
    payment_mode VARCHAR(30) NOT NULL,
    request_json TEXT NOT NULL,
    result_json TEXT NOT NULL,
    base_premium DECIMAL(14,2) NOT NULL,
    final_premium DECIMAL(14,2) NOT NULL,
    instalment_premium DECIMAL(14,2) NOT NULL
);
