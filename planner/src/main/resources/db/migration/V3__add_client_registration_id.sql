ALTER TABLE google_oauth2_credentials ADD COLUMN IF NOT EXISTS client_registration_id VARCHAR(50) NOT NULL DEFAULT 'google';
