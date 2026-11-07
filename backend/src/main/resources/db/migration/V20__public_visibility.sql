-- Tournaments can be shown publicly. A dedicated flag rather than a setting: it is a
-- visibility switch, not a rule, so it stays out of the settings JSONB document.
ALTER TABLE tournaments ADD COLUMN public_visibility BOOLEAN NOT NULL DEFAULT false;
