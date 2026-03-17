-- Rename 'ChargeLoad' to 'EmergencyChargeForceField' in selected_keywords SET column
-- MySQL SET is a positional bitmask: replacing a value at the same ordinal position
-- preserves existing data without needing row-level UPDATEs

ALTER TABLE planners MODIFY COLUMN selected_keywords SET(
    -- Status effects
    'Combustion', 'Laceration', 'Vibration', 'Burst', 'Sinking', 'Breath', 'Charge',
    -- Attack types
    'Slash', 'Penetrate', 'Hit',
    -- Affinities
    'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET',
    -- Ego gifts
    '9154',
    -- Synergy keywords
    'Assemble', 'KnowledgeExplored', 'AaCePcBt', 'SwordPlayOfTheHomeland',
    'EchoOfMansion', 'TimeSuspend', 'EmergencyChargeForceField', 'BloodDinner',
    'BlackCloud', 'RetaliationBook', 'HeishouSynergy',
    'Bullet'
);
