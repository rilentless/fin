-- Keep the newest active session for each user before enforcing the invariant.
WITH ranked_active_tokens AS (SELECT id,
                                     row_number() OVER (PARTITION BY user_id ORDER BY created_at DESC, id DESC) AS session_rank
                              FROM refresh_token
                              WHERE revoked = false)
UPDATE refresh_token token
SET revoked    = true,
    revoked_at = now()
FROM ranked_active_tokens ranked
WHERE token.id = ranked.id
  AND ranked.session_rank > 1;

-- Row locking in the service serializes normal issuance. This partial unique
-- index is the database backstop against two unrevoked sessions per user.
CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_token_one_active_session_per_user
    ON refresh_token (user_id)
    WHERE revoked = false;
