-- Profile fields have existed on users since V1 (bio 1000, location 100, URLs 500), but no
-- API wrote them before. This aligns their sizes with the profile API's limits. All stay
-- nullable. No application code ever stored values in them, so nothing is shortened; a
-- longer existing value would make this migration fail rather than be silently truncated
-- (MySQL strict mode).
ALTER TABLE users MODIFY COLUMN bio VARCHAR(500) NULL;
ALTER TABLE users MODIFY COLUMN location VARCHAR(120) NULL;
ALTER TABLE users MODIFY COLUMN website_url VARCHAR(255) NULL;
ALTER TABLE users MODIFY COLUMN github_url VARCHAR(255) NULL;
ALTER TABLE users MODIFY COLUMN linkedin_url VARCHAR(255) NULL;
