-- Add BlessingOfIndexPrescriptAlly (The Index) and Inspire (The Ring) to selected_keywords SET column

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
    'Inspire'
);
