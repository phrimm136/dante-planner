DELETE FROM domain_events WHERE event_type = 'PLANNER_CATALOG_CHANGED' AND dispatched_at IS NOT NULL;
