#!/usr/bin/env bash
# Brings up the local two-region rig and wires real GTID replication between the two MySQL
# servers, in the order RDS builds a read replica: let the source reach its final schema, clone
# it, then start replication from the clone's GTID position. Starting replication first instead
# would make the replica replay the container's own bootstrap DDL on top of its own, which fails
# on the duplicate database rather than converging.
#
# Prints the environment the e2e suites need. Lag is a knob once this is up:
#   docker exec limbusplanner-mysql-replica-1 mysql -uroot -p<pw> \
#     -e "STOP REPLICA SQL_THREAD;"      # freeze the replication window open
#   ... assert the gate pins reads to the primary ...
#     -e "START REPLICA SQL_THREAD;"     # release it
# or set a deterministic delay with CHANGE REPLICATION SOURCE TO SOURCE_DELAY=5.
set -euo pipefail

REPO=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
PROJECT=limbusplanner
PRIMARY_CT=${PRIMARY_CT:-limbusplanner-mysql-1}
REPLICA_CT=${REPLICA_CT:-limbusplanner-mysql-replica-1}
REPL_USER=repl
REPL_PW=repl

compose() {
  docker compose --project-directory "$REPO" \
    -f "$REPO/docker-compose.yml" \
    -f "$REPO/docker-compose.override.yml" \
    -f "$REPO/docker-compose.e2e.yml" \
    -f "$REPO/docker-compose.oauth-gtid.yml" \
    -f "$REPO/docker-compose.multiregion.yml" \
    -p "$PROJECT" "$@"
}

say() { printf '\n=== %s\n' "$*"; }

: "${STUB_CLIENT_SECRET:?set STUB_CLIENT_SECRET (the value the backend and stub share)}"
export STUB_CLIENT_SECRET

# Databases and support services first, backends last. Seoul routes read-only queries to its
# replica, so it cannot boot until the replica already holds the schema: starting it earlier
# means a startup query against an empty database, which no retry can fix.
say "1. starting the databases and support services"
compose up -d --build mysql mysql-replica redis-auth redis-ratelimit oauth-stub toxiproxy

root_pw=$(docker exec "$PRIMARY_CT" printenv MYSQL_ROOT_PASSWORD)
db=$(docker exec "$PRIMARY_CT" printenv MYSQL_DATABASE)

primary() { docker exec -i "$PRIMARY_CT" mysql -uroot -p"$root_pw" "$@"; }
replica() { docker exec -i "$REPLICA_CT" mysql -uroot -p"$root_pw" "$@"; }

say "2. migrating the primary"
until docker exec "$REPLICA_CT" mysqladmin ping -h 127.0.0.1 -uroot -p"$root_pw" --silent >/dev/null 2>&1; do sleep 2; done
until primary -e "SELECT 1" >/dev/null 2>&1; do sleep 2; done
# Flyway runs here rather than from a backend so the schema exists before either region starts.
docker run --rm --network container:"$PRIMARY_CT" \
  -v "$REPO/backend/src/main/resources/db/migration:/flyway/sql:ro" \
  flyway/flyway:11.7.2 \
  -url="jdbc:mysql://127.0.0.1:3306/$db?allowPublicKeyRetrieval=true&useSSL=false" \
  -user=root -password="$root_pw" -locations=filesystem:/flyway/sql \
  -validateOnMigrate=false migrate 2>&1 | grep -E "Successfully applied|already up to date|ERROR" | tail -2
primary "$db" -N -e "SELECT CONCAT('primary at V', MAX(CAST(version AS UNSIGNED))) FROM flyway_schema_history;" 2>/dev/null

say "3. creating the replication user on the primary"
# mysql_native_password: caching_sha2 needs either TLS or an RSA key exchange on the
# replication channel, which is ceremony this rig does not need.
primary -e "
  CREATE USER IF NOT EXISTS '$REPL_USER'@'%' IDENTIFIED WITH mysql_native_password BY '$REPL_PW';
  GRANT REPLICATION SLAVE ON *.* TO '$REPL_USER'@'%';
  FLUSH PRIVILEGES;" 2>/dev/null

say "4. cloning the primary into the replica"
# The replica's own entrypoint (database, app user) was binlogged, so its gtid_executed is
# non-empty and the dump's SET @@GLOBAL.GTID_PURGED would be refused. Clearing it first is what
# makes the clone's position authoritative.
replica -e "RESET MASTER;" 2>/dev/null
# Only the app schema: --all-databases would carry the primary's mysql.user table over the
# replica's own, cutting the connection this script is holding.
# --source-data=2 emits SET @@GLOBAL.GTID_PURGED, so the replica knows what it already contains.
docker exec "$PRIMARY_CT" mysqldump -uroot -p"$root_pw" \
  --databases "$db" --triggers --routines --events \
  --single-transaction --source-data=2 2>/dev/null > /tmp/mr-clone.sql
wc -c < /tmp/mr-clone.sql | xargs printf 'clone: %s bytes\n'
replica < /tmp/mr-clone.sql 2>/dev/null
rm -f /tmp/mr-clone.sql

say "5. starting replication"
replica -e "
  CHANGE REPLICATION SOURCE TO
    SOURCE_HOST='mysql', SOURCE_PORT=3306,
    SOURCE_USER='$REPL_USER', SOURCE_PASSWORD='$REPL_PW',
    SOURCE_AUTO_POSITION=1, GET_SOURCE_PUBLIC_KEY=1;
  START REPLICA;" 2>/dev/null
sleep 4
replica -e "SHOW REPLICA STATUS\G" 2>/dev/null \
  | grep -E "Replica_IO_Running|Replica_SQL_Running|Seconds_Behind_Source|Last_Error" | head -4

if ! replica -e "SHOW REPLICA STATUS\G" 2>/dev/null | grep -q "Replica_SQL_Running: Yes"; then
  echo "replication did not start; SHOW REPLICA STATUS above has the reason" >&2
  exit 1
fi

say "6. verifying a write reaches the replica"
probe="mr_probe_$$"
primary "$db" -e "CREATE TABLE IF NOT EXISTS $probe (id INT PRIMARY KEY); INSERT INTO $probe VALUES (1);" 2>/dev/null
sleep 2
if replica "$db" -N -e "SELECT COUNT(*) FROM $probe;" 2>/dev/null | grep -q 1; then
  echo "replication verified end to end"
else
  echo "the probe row did not arrive: replication is running but not applying" >&2
  exit 1
fi
primary "$db" -e "DROP TABLE $probe;" 2>/dev/null

say "7. starting both regions"
compose up -d --build
for port in 8081 8082; do
  until curl -fsS -o /dev/null --max-time 5 "http://localhost:$port/actuator/health" 2>/dev/null; do sleep 3; done
  echo "backend on :$port is up"
done

say "ready — run the suites with"
cat <<'ENV'
  E2E_BASE_URL=http://localhost \
  E2E_API_URL=http://localhost:8082 \
  E2E_APP_URL=http://localhost \
  E2E_API_SEOUL_URL=http://localhost:8082 \
  E2E_API_OREGON_URL=http://localhost:8081 \
  E2E_IDP_URL=http://localhost:3000 \
  E2E_DB_HOST=127.0.0.1 E2E_DB_USER=<app user> E2E_DB_PASSWORD=<app password> \
  yarn --cwd e2e playwright test tests/read-your-writes.spec.ts
ENV
