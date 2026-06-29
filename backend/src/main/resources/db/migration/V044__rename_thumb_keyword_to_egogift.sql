-- Replace 'AccelBullet' with the ego-gift id '9828' for the The Thumb planner keyword (icon swap).
-- MySQL 8.0 strict mode rejects in-place SET member renames when rows contain the old value.
-- Safe approach: add new member → migrate data → remove old member.

-- Step 1: Add 9828 to the SET (keep AccelBullet for now)
ALTER TABLE planners MODIFY COLUMN selected_keywords SET(
    'Combustion', 'Laceration', 'Vibration', 'Burst', 'Sinking', 'Breath', 'Charge',
    'Slash', 'Penetrate', 'Hit',
    'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET',
    '9154',
    'Assemble', 'KnowledgeExplored', 'AaCePcBt', 'SwordPlayOfTheHomeland',
    'EchoOfMansion', 'TimeSuspend', 'EmergencyChargeForceField', 'BloodDinner',
    'BlackCloud', 'RetaliationBook', 'HeishouSynergy',
    'Bullet',
    'BlessingOfIndexPrescriptAlly',
    'Inspire',
    'AccelBullet',
    'SojiRyoshuEntangle',
    '9828'
);

-- Step 2: Migrate existing data from AccelBullet to 9828
UPDATE planners
SET selected_keywords = TRIM(BOTH ',' FROM
    REPLACE(CONCAT(',', selected_keywords, ','), ',AccelBullet,', ',9828,'))
WHERE FIND_IN_SET('AccelBullet', selected_keywords) > 0;

-- Step 3: Remove AccelBullet from the SET definition
ALTER TABLE planners MODIFY COLUMN selected_keywords SET(
    'Combustion', 'Laceration', 'Vibration', 'Burst', 'Sinking', 'Breath', 'Charge',
    'Slash', 'Penetrate', 'Hit',
    'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET',
    '9154',
    'Assemble', 'KnowledgeExplored', 'AaCePcBt', 'SwordPlayOfTheHomeland',
    'EchoOfMansion', 'TimeSuspend', 'EmergencyChargeForceField', 'BloodDinner',
    'BlackCloud', 'RetaliationBook', 'HeishouSynergy',
    'Bullet',
    'BlessingOfIndexPrescriptAlly',
    'Inspire',
    '9828',
    'SojiRyoshuEntangle'
);
