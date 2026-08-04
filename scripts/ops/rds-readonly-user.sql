-- One-time bootstrap for the read-only access user (scripts/ops/access/rds-query.sh).
-- Run against the PRIMARY (danteplanner-mysql) as an admin user; the replica
-- receives the grants through replication. Rerunnable, and the ALTER USER makes
-- a rerun converge: CREATE ... IF NOT EXISTS alone would silently keep an old
-- password. Host is scoped to the VPC (connections arrive via the SSM tunnel's
-- app-node hop, never the internet); MAX_USER_CONNECTIONS caps a runaway client
-- against the shared pool. The password lives in Secrets Manager
-- (provision/rds-readonly-secrets.sh, run FIRST); rds-bootstrap-readonly-user.sh
-- prepends `SET @pw_hex = '<hex>';` so this file never carries it — see the
-- one-time setup in docs/external-access.md.

SET @pw = CONVERT(UNHEX(@pw_hex) USING utf8mb4);

SET @stmt = CONCAT('CREATE USER IF NOT EXISTS ''danteplanner_ro''@''10.%'' IDENTIFIED BY ',
                   QUOTE(@pw));
PREPARE create_ro FROM @stmt;
EXECUTE create_ro;
DEALLOCATE PREPARE create_ro;

SET @stmt = CONCAT('ALTER USER ''danteplanner_ro''@''10.%'' IDENTIFIED BY ',
                   QUOTE(@pw), ' WITH MAX_USER_CONNECTIONS 3');
PREPARE alter_ro FROM @stmt;
EXECUTE alter_ro;
DEALLOCATE PREPARE alter_ro;

GRANT SELECT ON danteplanner.* TO 'danteplanner_ro'@'10.%';

FLUSH PRIVILEGES;
