-- first_published_at cannot be NOT NULL: planner_publication is a 1:1 satellite attached when the
-- planner is created, so a row exists for planners that have never been published and have no
-- publish moment to record.
--
-- It is also not a mirror of `published`. The timestamp is retained when a planner is unpublished,
-- so `published = FALSE` rows legitimately carry one -- it records that publication once happened,
-- not that it currently holds. Only the forward implication is an invariant.

ALTER TABLE planner_publication
    ADD CONSTRAINT ck_publication_published_has_timestamp
    CHECK (published = FALSE OR first_published_at IS NOT NULL);
