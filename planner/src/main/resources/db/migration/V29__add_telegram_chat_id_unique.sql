-- Clean up duplicate telegram_chat_id values (keep the latest connected one)
UPDATE trainees t
SET telegram_chat_id = NULL,
    telegram_username = NULL,
    telegram_connected_at = NULL
WHERE t.id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY telegram_chat_id
            ORDER BY telegram_connected_at DESC NULLS LAST, id DESC
        ) AS rn
        FROM trainees
        WHERE telegram_chat_id IS NOT NULL
    ) dups
    WHERE dups.rn > 1
);

-- Add unique constraint on telegram_chat_id for trainees
ALTER TABLE trainees ADD CONSTRAINT uk_trainees_telegram_chat_id UNIQUE (telegram_chat_id);

-- Do the same for mentors table (for consistency)
UPDATE mentors m
SET telegram_chat_id = NULL,
    telegram_username = NULL,
    telegram_connected_at = NULL
WHERE m.id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY telegram_chat_id
            ORDER BY telegram_connected_at DESC NULLS LAST, id DESC
        ) AS rn
        FROM mentors
        WHERE telegram_chat_id IS NOT NULL
    ) dups
    WHERE dups.rn > 1
);

ALTER TABLE mentors ADD CONSTRAINT uk_mentors_telegram_chat_id UNIQUE (telegram_chat_id);
