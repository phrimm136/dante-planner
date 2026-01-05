-- Change category from ENUM to VARCHAR to support multiple planner types
-- Preserves existing MDCategory values (5F, 10F, 15F)
ALTER TABLE planners MODIFY COLUMN category VARCHAR(50) NOT NULL;
