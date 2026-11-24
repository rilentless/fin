-- Seed 1,001 test accounts for development and load testing.
-- All accounts use the password: password12345
INSERT INTO users (
    id,
    created_at,
    updated_at,
    full_name,
    email,
    password_hash,
    role,
    account_status,
    deletion_requested_at,
    free_chat_count
)
SELECT
    gen_random_uuid()::VARCHAR,
    now(),
    now(),
    format('Seed User %s', lpad(seed_number::TEXT, 4, '0')),
    format('seed.user%s@fin.ai', lpad(seed_number::TEXT, 4, '0')),
    '$2a$10$y8RxtjTTBHEp2GemllrBb.BJBVH.LEj6hDoSJ4J1KzqovZzMbXkGa',
    'USER',
    'ACTIVE',
    NULL,
    0
FROM generate_series(1, 1001) AS seed_number
ON CONFLICT (email) DO NOTHING;
