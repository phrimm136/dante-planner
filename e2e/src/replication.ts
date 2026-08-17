import { execFileSync } from 'node:child_process'

/** The rig's replica container (scripts/ops/local-multiregion-up.sh REPLICA_CT default). */
export const REPLICA_CONTAINER =
  process.env.E2E_REPLICA_CONTAINER ?? 'limbusplanner-mysql-replica-1'

function replicaSql(statement: string): void {
  const rootPassword = execFileSync(
    'docker',
    ['exec', REPLICA_CONTAINER, 'printenv', 'MYSQL_ROOT_PASSWORD'],
    { encoding: 'utf8' },
  ).trim()
  execFileSync(
    'docker',
    ['exec', REPLICA_CONTAINER, 'mysql', '-uroot', `-p${rootPassword}`, '-e', statement],
    { stdio: 'pipe' },
  )
}

export function replicaReachable(): boolean {
  try {
    execFileSync('docker', ['inspect', REPLICA_CONTAINER], { stdio: 'pipe' })
    return true
  } catch {
    return false
  }
}

/**
 * Holds the replication window open by stopping only the SQL applier. The IO thread keeps
 * pulling the binlog, so release replays events the replica already holds instead of paying a
 * reconnect-and-catch-up whose duration nothing in a test can bound.
 */
export function freezeReplication(): void {
  replicaSql('STOP REPLICA SQL_THREAD;')
}

export function releaseReplication(): void {
  replicaSql('START REPLICA SQL_THREAD;')
}
