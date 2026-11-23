-- Version 0.2: blogs and comments remember when they were written.
-- Existing rows get the time of the migration, their real creation time is unknown.
-- The column is filled before it becomes NOT NULL, otherwise the change fails on existing data.

ALTER TABLE Blog ADD COLUMN createdAt DATETIME(6) NULL;
UPDATE Blog SET createdAt = CURRENT_TIMESTAMP(6) WHERE createdAt IS NULL;
ALTER TABLE Blog MODIFY createdAt DATETIME(6) NOT NULL;

ALTER TABLE BlogComment ADD COLUMN createdAt DATETIME(6) NULL;
UPDATE BlogComment SET createdAt = CURRENT_TIMESTAMP(6) WHERE createdAt IS NULL;
ALTER TABLE BlogComment MODIFY createdAt DATETIME(6) NOT NULL;
