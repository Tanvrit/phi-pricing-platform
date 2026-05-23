CREATE TABLE IF NOT EXISTS buyonline_session (
    session_id   VARCHAR(64) PRIMARY KEY,
    state_json   TEXT NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
