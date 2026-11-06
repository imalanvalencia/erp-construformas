-- V15: admin password resets hand out a temporary password and force the user to pick
-- a new one on the next login (S10). Cleared by a successful self-service password change.
ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
