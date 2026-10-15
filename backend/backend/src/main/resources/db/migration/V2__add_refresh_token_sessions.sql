-- Only hashes are retained; consumed tokens remain until their absolute expiry
-- so replay can revoke the entire session family. No financial table is changed.
CREATE TABLE refresh_token_sessions (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    rotated_at TIMESTAMPTZ,
    CONSTRAINT refresh_token_hash_check CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT refresh_token_expiry_check CHECK (expires_at > created_at),
    CONSTRAINT refresh_token_revocation_check CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT refresh_token_rotation_check CHECK (rotated_at IS NULL OR (revoked_at IS NOT NULL AND rotated_at >= created_at))
);
CREATE INDEX refresh_token_sessions_user_idx ON refresh_token_sessions(user_id);
CREATE INDEX refresh_token_sessions_family_idx ON refresh_token_sessions(family_id);
CREATE INDEX refresh_token_sessions_expiry_idx ON refresh_token_sessions(expires_at);
