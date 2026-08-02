#!/usr/bin/env bash
# Times the V046..V053 decomposition against SYNTHETIC data at a chosen row count.
#
# Complements tmp/migration-rehearsal.sh (which restores a production dump): that one answers
# "does the chain survive real skew", this one answers "how long does the window take at N rows"
# and can be pushed past production's current size to see where the number goes. It needs no
# production access and carries no PII, so it is safe in CI and on any machine.
#
# The row mix deliberately includes the shapes V050/V051 defend against — a malformed device_id,
# NULL and empty keyword sets, legacy keyword names awaiting rename, 4-byte characters, and
# soft-deleted rows — because a backfill timed only against clean rows measures the happy path.
#
# Usage:  ROWS=200000 ./migration-rehearsal-synthetic.sh
#         KEEP=1 ROWS=1000 ./migration-rehearsal-synthetic.sh   # leave the container up
set -euo pipefail

REPO=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
MIGRATIONS="$REPO/backend/src/main/resources/db/migration"
ROWS=${ROWS:-50000}
CONTAINER=${CONTAINER:-rehearsal-synthetic-mysql}
LOCAL_PORT=${LOCAL_PORT:-13400}
ROOT_PW=rehearsal

# Must track the production ENGINE: 8.4 changed defaults and removed syntax 8.0 accepts, so a
# rehearsal on the wrong line proves nothing about the window.
SERVER_IMAGE=${SERVER_IMAGE:-mysql:8.0}
FLYWAY_VERSION=${FLYWAY_VERSION:-11.7.2}

say() { printf '\n=== %s\n' "$*"; }
mysql_root() { docker exec -i "$CONTAINER" mysql -h 127.0.0.1 -uroot -p"$ROOT_PW" --silent "$@"; }

cleanup() {
  if [[ "${KEEP:-0}" == "1" ]]; then
    echo "container left up: docker exec -it $CONTAINER mysql -uroot -p$ROOT_PW danteplanner"
  else
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

say "1. starting $SERVER_IMAGE"
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER" \
  -e MYSQL_ROOT_PASSWORD="$ROOT_PW" -e MYSQL_DATABASE=danteplanner \
  -p "$LOCAL_PORT:3306" "$SERVER_IMAGE" \
  --sql-mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' \
  >/dev/null

until docker exec "$CONTAINER" mysqladmin ping -h 127.0.0.1 -uroot -p"$ROOT_PW" --silent >/dev/null 2>&1; do
  sleep 2
done
docker exec "$CONTAINER" mysql -uroot -p"$ROOT_PW" -e "SELECT VERSION();" 2>/dev/null | tail -1

flyway() { # target...
  docker run --rm --network host \
    -v "$MIGRATIONS:/flyway/sql:ro" \
    "flyway/flyway:$FLYWAY_VERSION" \
    -url="jdbc:mysql://127.0.0.1:$LOCAL_PORT/danteplanner?allowPublicKeyRetrieval=true&useSSL=false" \
    -user=root -password="$ROOT_PW" \
    -locations=filesystem:/flyway/sql \
    -validateOnMigrate=false -outOfOrder=false "$@"
}

say "2. building the pre-decomposition schema (Flyway -> V045)"
flyway -target=45 migrate 2>&1 | grep -E "Successfully applied|Migrating|ERROR" | tail -3

say "3. generating $ROWS synthetic planners rows"
# Generated inside the server with a recursive CTE: no multi-hundred-megabyte SQL file to write,
# parse and ship, which at high row counts dwarfs the migration being measured.
docker exec -i "$CONTAINER" mysql -h 127.0.0.1 -uroot -p"$ROOT_PW" danteplanner <<SQL
SET SESSION cte_max_recursion_depth = 100000000;

INSERT INTO users (id, public_id, email, provider, provider_id, username_epithet, username_suffix, role, created_at)
VALUES (1, UUID_TO_BIN(UUID()), 'synthetic@example.invalid', 'GOOGLE', 'synthetic-1', 'Faust', 'AAAAA', 'NORMAL', NOW())
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO planners (
    id, user_id, title, category, status, content, content_version, planner_type,
    version, sync_version, device_id, created_at, last_modified_at, deleted_at, selected_keywords,
    published, first_published_at, taken_down_at
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < $ROWS
)
SELECT
    UUID_TO_BIN(UUID()),
    1,
    -- One row in fifty carries 4-byte characters: utf8mb4 handling is exactly the kind of thing
    -- a column-copying backfill gets wrong, and it never appears in hand-written seeds.
    CASE WHEN n % 50 = 0 THEN CONCAT('synthetic ', n, ' 🔥🌑') ELSE CONCAT('synthetic ', n) END,
    ELT(1 + (n % 3), '5F', '10F', '15F'),
    IF(n % 4 = 0, 'draft', 'saved'),
    JSON_OBJECT(
        'selectedKeywords', JSON_ARRAY(),
        'equipment', JSON_OBJECT(
            '01', JSON_OBJECT('identity', JSON_OBJECT('id', '10101', 'uptie', 1, 'level', 1),
                              'egos', JSON_OBJECT('ZAYIN', JSON_OBJECT('id', '20101', 'threadspin', 1))),
            '02', JSON_OBJECT('identity', JSON_OBJECT('id', '10201', 'uptie', 1, 'level', 1),
                              'egos', JSON_OBJECT('ZAYIN', JSON_OBJECT('id', '20201', 'threadspin', 1)))
        ),
        'deploymentOrder', JSON_ARRAY(),
        'floorSelections', JSON_ARRAY(),
        'sectionNotes', JSON_OBJECT('n', REPEAT('x', 400))
    ),
    7,
    IF(n % 7 = 0, 'REFRACTED_RAILWAY', 'MIRROR_DUNGEON'),
    1,
    1,
    -- One row in ten has a device_id the backfill's REGEXP must reject rather than convert.
    IF(n % 10 = 0, 'not-a-uuid', UUID()),
    NOW() - INTERVAL (n % 500) DAY,
    NOW() - INTERVAL (n % 250) DAY,
    -- Soft-deleted rows ride the aggregate but must stay out of the visible-only catalog.
    IF(n % 25 = 0, NOW() - INTERVAL (n % 30) DAY, NULL),
    -- NULL, empty and multi-member keyword sets — the three shapes V050's CASE branches on.
    -- Only current members appear: selected_keywords is a SET, and V038/V044 already migrated
    -- the legacy names out, so a SET column cannot physically hold the values V050's REPLACE
    -- still guards against.
    CASE n % 8
        WHEN 0 THEN NULL
        WHEN 1 THEN ''
        WHEN 2 THEN '9828'
        WHEN 3 THEN 'EmergencyChargeForceField'
        WHEN 4 THEN 'Combustion,Slash'
        WHEN 5 THEN 'Sinking,AZURE,Assemble'
        WHEN 6 THEN 'Burst'
        ELSE 'Laceration,Hit,CRIMSON'
    END,
    -- Most rows must be PUBLISHED or the two backfills that matter measure nothing: the catalog
    -- is seeded from visible rows only, and the keyword filter indexes what the catalog holds.
    (n % 10) < 6,
    IF((n % 10) < 6, NOW() - INTERVAL (n % 400) DAY, NULL),
    -- Taken-down rows are published yet must stay out of the catalog: the other visibility edge.
    IF(n % 40 = 0, NOW() - INTERVAL (n % 20) DAY, NULL)
FROM seq;

SELECT COUNT(*) AS planners_rows FROM planners;
SQL

say "4. applying V046..V053"
start=$(date +%s)
flyway migrate 2>&1 | grep -E "Successfully applied|ERROR|Migration.*failed" | tail -3
elapsed=$(( $(date +%s) - start ))

say "5. per-migration time at $ROWS rows"
mysql_root danteplanner -e "
  SELECT version, LEFT(description, 44) AS migration,
         execution_time AS ms, success
    FROM flyway_schema_history
   WHERE CAST(version AS UNSIGNED) >= 46
   ORDER BY installed_rank;
  SELECT SUM(execution_time) AS total_ms_v046_onward
    FROM flyway_schema_history WHERE CAST(version AS UNSIGNED) >= 46;"

say "6. what landed"
mysql_root danteplanner -e "
  SELECT
    (SELECT COUNT(*) FROM planner)               AS aggregate_rows,
    (SELECT COUNT(*) FROM planner_content)       AS content_rows,
    (SELECT COUNT(*) FROM planner_catalog)       AS catalog_visible_rows,
    (SELECT COUNT(*) FROM planner_keyword_filter) AS keyword_filter_rows;"

echo
echo "wall clock for step 4: ${elapsed}s"
echo "DIRECTIONAL: local NVMe and this container's buffer pool are not a db.t4g.micro."
echo "Scale the window from the trend across row counts, not from one absolute number."
