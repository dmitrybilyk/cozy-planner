INSERT INTO users (email, name, google_sub, created_at)
VALUES ('demo@cozyplanner.app', 'Демо', 'demo-seed', CURRENT_TIMESTAMP);

UPDATE clubs SET user_id = (SELECT id FROM users WHERE google_sub = 'demo-seed')
WHERE name = 'Спортивний Hub';
