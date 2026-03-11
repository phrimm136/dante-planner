-- Add Bullet keyword to the selected_keywords SET column

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
    'EchoOfMansion', 'TimeSuspend', 'ChargeLoad', 'BloodDinner',
    'BlackCloud', 'RetaliationBook', 'HeishouSynergy',
    'Bullet'
);
