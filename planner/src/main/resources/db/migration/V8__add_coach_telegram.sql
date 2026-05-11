ALTER TABLE coaches ADD COLUMN telegram_chat_id TEXT;
ALTER TABLE coaches ADD COLUMN telegram_username TEXT;
ALTER TABLE coaches ADD COLUMN telegram_connected_at TIMESTAMP;
ALTER TABLE coaches ADD COLUMN telegram_token VARCHAR(64);
