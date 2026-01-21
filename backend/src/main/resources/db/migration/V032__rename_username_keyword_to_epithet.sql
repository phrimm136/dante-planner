-- Rename username_keyword column to username_epithet
-- Migrate existing association keywords to random epithets

-- Step 1: Rename the column
ALTER TABLE users RENAME COLUMN username_keyword TO username_epithet;

-- Step 2: Create a temporary table with epithet options for random assignment
CREATE TEMPORARY TABLE epithet_options (
    id SERIAL PRIMARY KEY,
    epithet VARCHAR(50) NOT NULL
);

INSERT INTO epithet_options (epithet) VALUES
    ('NAIVE'), ('STUPID'), ('RATIONAL'), ('BRILLIANT'), ('UNBENDING'),
    ('PROACTIVE'), ('RESOURCEFUL'), ('AUGUST'), ('DIGNIFIED'), ('LOVELY'),
    ('GUILEFUL'), ('ASTUTE'), ('INTELLIGENT'), ('CURIOUS'), ('FORSAKEN'),
    ('ZEALOUS'), ('METHODICAL'), ('METICULOUS'), ('DILIGENT'), ('POETIC'),
    ('ELEGANT'), ('THOROUGH'), ('ATTUNED'), ('LOYAL'), ('COMPOSED'),
    ('BLIND');

-- Step 3: Update existing users with random epithets
-- Uses a deterministic random based on user id for reproducibility
UPDATE users u
SET username_epithet = (
    SELECT epithet
    FROM epithet_options
    ORDER BY (u.id * 7919 + 104729) % 26 + 1
    LIMIT 1
);

-- Step 4: Cleanup
DROP TABLE epithet_options;
