-- Add the Dawn Office planner keyword. Appending at the end of the SET is safe;
-- existing bitmask positions are unchanged.
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
    'SojiRyoshuEntangle',
    'DawnTeam'
);
