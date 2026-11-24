-- Seed 10,001 mixed transactions for load testing user seed.user0001@fin.ai.
-- Login with email: seed.user0001@fin.ai
-- Login with password: password12345
WITH seed_user AS (
    SELECT id
    FROM users
    WHERE email = 'seed.user0001@fin.ai'
)
INSERT INTO transactions (
    id,
    created_at,
    updated_at,
    amount,
    type,
    date,
    note,
    embedding_id,
    user_id,
    category_id,
    payment_mode_id
)
SELECT
    gen_random_uuid()::VARCHAR,
    now() - (transaction_number * INTERVAL '1 minute'),
    now() - (transaction_number * INTERVAL '1 minute'),
    (25 + (transaction_number * 37) % 9975)::NUMERIC(12, 2),
    CASE WHEN transaction_number % 2 = 0 THEN 'INCOME' ELSE 'EXPENSE' END,
    CURRENT_DATE - (transaction_number % 730),
    CASE
        WHEN transaction_number % 2 = 0 THEN format('Load test income record %s', transaction_number)
        ELSE format('Load test expense record %s', transaction_number)
    END,
    NULL,
    seed_user.id,
    (
        SELECT id
        FROM categories
        WHERE user_id IS NULL
          AND category_type = CASE
                                  WHEN transaction_number % 2 = 0 THEN 'INCOME'
                                  ELSE 'EXPENSE'
                              END
        ORDER BY id
        OFFSET ((transaction_number - 1) % 6)
        LIMIT 1
    ),
    (
        SELECT id
        FROM payment_modes
        WHERE user_id IS NULL
        ORDER BY id
        OFFSET ((transaction_number - 1) % 6)
        LIMIT 1
    )
FROM generate_series(1, 10001) AS seed_data(transaction_number)
CROSS JOIN seed_user
WHERE NOT EXISTS (
    SELECT 1
    FROM transactions existing
    WHERE existing.user_id = seed_user.id
)
ON CONFLICT (id) DO NOTHING;
