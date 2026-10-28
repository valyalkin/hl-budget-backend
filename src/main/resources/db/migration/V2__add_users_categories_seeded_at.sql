-- When the 17 default categories were created for this user (research R-003). NULL means not
-- yet: users from before feature 002, and new users until their first budgeting call.
ALTER TABLE users ADD COLUMN categories_seeded_at timestamptz NULL;
