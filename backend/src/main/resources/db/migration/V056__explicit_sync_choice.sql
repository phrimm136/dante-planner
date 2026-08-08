-- Splits the sync preference into the preference itself and whether the user has answered
-- the first-login sync prompt. A NULL sync_enabled previously carried both facts.

ALTER TABLE user_settings
    ADD COLUMN sync_choice_made BOOLEAN NOT NULL DEFAULT FALSE AFTER sync_enabled;

UPDATE user_settings SET sync_choice_made = TRUE WHERE sync_enabled IS NOT NULL;

UPDATE user_settings SET sync_enabled = FALSE WHERE sync_enabled IS NULL;

ALTER TABLE user_settings
    MODIFY COLUMN sync_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_settings
    ADD CONSTRAINT ck_user_settings_sync_choice
    CHECK (sync_choice_made = TRUE OR sync_enabled = FALSE);
