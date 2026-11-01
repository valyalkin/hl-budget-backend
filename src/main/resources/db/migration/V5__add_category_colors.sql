-- Category colours (specs/007-fix-pie-csv-categories, research R-003 to R-005).
--
-- Every category gets a fixed #rrggbb colour. The 17 defaults get theirs by name. Existing custom
-- categories get the colour the app would have given them, in creation order: the 7 spare colours
-- first, then the order spare (18-24), defaults (1-17), repeating. The app assigns colours to new
-- categories itself. The default value only keeps the previous build working during a rolling
-- deploy (it inserts categories without a colour); it is a neutral grey outside the palette.

ALTER TABLE categories ADD COLUMN color varchar(7) NOT NULL DEFAULT '#8a908b';

WITH defaults (name, color) AS (
  VALUES
    ('Rent', '#1e5b45'),
    ('Groceries', '#c2410c'),
    ('Public Transport', '#1d4ed8'),
    ('Car', '#a16207'),
    ('Household', '#7e22ce'),
    ('Health', '#0f766e'),
    ('Beauty', '#be185d'),
    ('Clothes', '#4d7c0f'),
    ('Education', '#b91c1c'),
    ('Travel', '#0369a1'),
    ('Bills', '#57534e'),
    ('Subscriptions', '#4338ca'),
    ('Other', '#64748b'),
    ('Sport', '#16a34a'),
    ('Entertainment', '#c026d3'),
    ('Pet', '#7c2d12'),
    ('Taxi', '#8a6d00')
)
UPDATE categories c
SET color = d.color
FROM defaults d
WHERE c.is_default AND c.name = d.name;

-- idx is the position in the tie order: 0-6 the spare colours, 7-23 the defaults' colours.
WITH tie_order (idx, color) AS (
  VALUES
    (0, '#e11d48'), (1, '#0891b2'), (2, '#a855f7'), (3, '#365314'), (4, '#1e3a8a'), (5, '#701a75'),
    (6, '#ea580c'), (7, '#1e5b45'), (8, '#c2410c'), (9, '#1d4ed8'), (10, '#a16207'), (11, '#7e22ce'),
    (12, '#0f766e'), (13, '#be185d'), (14, '#4d7c0f'), (15, '#b91c1c'), (16, '#0369a1'), (17, '#57534e'),
    (18, '#4338ca'), (19, '#64748b'), (20, '#16a34a'), (21, '#c026d3'), (22, '#7c2d12'), (23, '#8a6d00')
),
numbered AS (
  SELECT id, row_number() OVER (PARTITION BY user_id ORDER BY created_at, id) AS rn
  FROM categories
  WHERE NOT is_default
)
UPDATE categories c
SET color = t.color
FROM numbered n
JOIN tie_order t ON t.idx = CASE WHEN n.rn <= 7 THEN n.rn - 1 ELSE (n.rn - 8) % 24 END
WHERE c.id = n.id;

ALTER TABLE categories
  ADD CONSTRAINT ck_categories_color CHECK (color ~ '^#[0-9a-f]{6}$');
