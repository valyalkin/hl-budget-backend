-- Account colours (specs/008-account-breakdown, research R-001 to R-003).
--
-- Every account gets a fixed #rrggbb colour from the category palette. Existing accounts get the
-- colour the app would have given them, in creation order per user: the 7 spare colours first,
-- then the 17 default category colours, repeating. This is the order CategoryPalette.next gives
-- when accounts are created one by one. The app assigns colours to new accounts itself. The
-- default value only keeps the previous build working during a rolling deploy (it inserts
-- accounts without a colour); it is a neutral grey outside the palette.

ALTER TABLE accounts ADD COLUMN color varchar(7) NOT NULL DEFAULT '#8a908b';

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
  FROM accounts
)
UPDATE accounts a
SET color = t.color
FROM numbered n
JOIN tie_order t ON t.idx = (n.rn - 1) % 24
WHERE a.id = n.id;

ALTER TABLE accounts
  ADD CONSTRAINT ck_accounts_color CHECK (color ~ '^#[0-9a-f]{6}$');
