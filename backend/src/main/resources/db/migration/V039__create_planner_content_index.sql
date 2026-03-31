CREATE TABLE planner_content_index (
    planner_id BINARY(16) NOT NULL,
    entity_type ENUM('IDENTITY', 'EGO', 'EGO_GIFT', 'THEME_PACK') NOT NULL,
    entity_id VARCHAR(20) NOT NULL,

    PRIMARY KEY (entity_type, entity_id, planner_id),
    INDEX idx_planner (planner_id),
    CONSTRAINT fk_pci_planner FOREIGN KEY (planner_id)
        REFERENCES planners(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
