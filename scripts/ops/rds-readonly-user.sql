-- One-time bootstrap for the read-only access user (scripts/ops/access/rds-query.sh).
-- Run against the PRIMARY (danteplanner-mysql) as an admin user; the replica
-- receives the grants through replication. NEVER execute while the literal
-- REPLACE_ME is still present — this file is public, so that password is too.
-- Rerunnable, and the ALTER USER makes a rerun converge: CREATE ... IF NOT
-- EXISTS alone would silently keep an old password. Host is scoped to the VPC
-- (connections arrive via the SSM tunnel's app-node hop, never the internet);
-- MAX_USER_CONNECTIONS caps a runaway client against the shared pool. The real
-- password lives in Secrets Manager (provision/rds-readonly-secrets.sh, run
-- FIRST) and is substituted for REPLACE_ME at execution time — see the
-- one-time setup in docs/external-access.md.

CREATE USER IF NOT EXISTS 'danteplanner_ro'@'10.%' IDENTIFIED BY 'REPLACE_ME';
ALTER USER 'danteplanner_ro'@'10.%' IDENTIFIED BY 'REPLACE_ME'
    WITH MAX_USER_CONNECTIONS 3;
GRANT SELECT ON danteplanner.* TO 'danteplanner_ro'@'10.%';

FLUSH PRIVILEGES;
