-- #131: co-organizers. The lead organizer is tournaments.owner_id and is never a row
-- here; each row grants one more admin the organizer rights on one tournament. Both
-- references cascade, so deleting a tournament or an admin removes its grants.
-- The primary key already serves lookups by tournament; the index serves the reverse
-- lookup "which tournaments does this admin co-organize".

CREATE TABLE tournament_co_organizers (
    tournament_id VARCHAR(36) NOT NULL REFERENCES tournaments (id) ON DELETE CASCADE,
    admin_user_id VARCHAR(36) NOT NULL REFERENCES admin_users (id) ON DELETE CASCADE,
    added_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tournament_id, admin_user_id)
);

CREATE INDEX tournament_co_organizers_admin_user_id_idx ON tournament_co_organizers (admin_user_id);
