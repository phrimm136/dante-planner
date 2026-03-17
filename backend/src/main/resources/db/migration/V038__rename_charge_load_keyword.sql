-- Rename 'ChargeLoad' to 'EmergencyChargeForceField' in selected_keywords SET column
-- MySQL 8.0 strict mode rejects in-place SET member renames when rows contain the old value.
-- Safe approach: add new member → migrate data → remove old member.

-- Step 1: Add EmergencyChargeForceField to the SET (keep ChargeLoad for now)
ALTER TABLE planners MODIFY COLUMN selected_keywords SET(
    'Combustion', 'Laceration', 'Vibration', 'Burst', 'Sinking', 'Breath', 'Charge',
    'Slash', 'Penetrate', 'Hit',
    'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET',
    '9154',
    'Assemble', 'KnowledgeExplored', 'AaCePcBt', 'SwordPlayOfTheHomeland',
    'EchoOfMansion', 'TimeSuspend', 'ChargeLoad', 'BloodDinner',
    'BlackCloud', 'RetaliationBook', 'HeishouSynergy',
    'Bullet',
    'EmergencyChargeForceField'
);

-- Step 2: Migrate existing data from ChargeLoad to EmergencyChargeForceField
UPDATE planners
SET selected_keywords = TRIM(BOTH ',' FROM
    REPLACE(CONCAT(',', selected_keywords, ','), ',ChargeLoad,', ',EmergencyChargeForceField,'))
WHERE FIND_IN_SET('ChargeLoad', selected_keywords) > 0;

-- Step 3: Remove ChargeLoad from the SET definition
ALTER TABLE planners MODIFY COLUMN selected_keywords SET(
    'Combustion', 'Laceration', 'Vibration', 'Burst', 'Sinking', 'Breath', 'Charge',
    'Slash', 'Penetrate', 'Hit',
    'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET',
    '9154',
    'Assemble', 'KnowledgeExplored', 'AaCePcBt', 'SwordPlayOfTheHomeland',
    'EchoOfMansion', 'TimeSuspend', 'EmergencyChargeForceField', 'BloodDinner',
    'BlackCloud', 'RetaliationBook', 'HeishouSynergy',
    'Bullet'
);
