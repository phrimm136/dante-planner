-- Add new planner keywords to the selected_keywords SET column
-- New keywords: Assemble, KnowledgeExplored, AaCePcBt, SwordPlayOfTheHomeland,
--               EchoOfMansion, TimeSuspend, ChargeLoad, BloodDinner,
--               BlackCloud, RetaliationBook, HeishouSynergy

ALTER TABLE planners MODIFY COLUMN selected_keywords SET(
    -- Original status effects
    'Combustion', 'Laceration', 'Vibration', 'Burst', 'Sinking', 'Breath', 'Charge',
    -- Original attack types
    'Slash', 'Penetrate', 'Hit',
    -- Original affinities
    'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET',
    -- New synergy keywords
    'Assemble', 'KnowledgeExplored', 'AaCePcBt', 'SwordPlayOfTheHomeland',
    'EchoOfMansion', 'TimeSuspend', 'ChargeLoad', 'BloodDinner',
    'BlackCloud', 'RetaliationBook', 'HeishouSynergy'
);
