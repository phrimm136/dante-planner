import mysql from 'mysql2/promise'

// Accounts are created only by the OAuth callback, so a suite that needs one makes it here. The
// connection is plain TCP to a local port the runner forwards to RDS with
// scripts/ops/access/rds-tunnel.sh; seeding writes, so the tunnel must point at the primary.
const DB_HOST = process.env.E2E_DB_HOST ?? '127.0.0.1'
const DB_PORT = Number(process.env.E2E_DB_PORT ?? 3306)
const DB_NAME = process.env.E2E_DB_DATABASE ?? 'danteplanner'
const POOL_SIZE = 4

type Row = Record<string, unknown>

let pool: mysql.Pool | undefined

function db(): mysql.Pool {
  if (pool) return pool

  const user = process.env.E2E_DB_USER
  const password = process.env.E2E_DB_PASSWORD
  if (!user || !password) {
    throw new Error(
      'E2E_DB_USER and E2E_DB_PASSWORD must be set, and scripts/ops/access/rds-tunnel.sh start ' +
        `must be forwarding ${DB_HOST}:${DB_PORT}`,
    )
  }

  pool = mysql.createPool({
    host: DB_HOST,
    port: DB_PORT,
    user,
    password,
    database: DB_NAME,
    connectionLimit: POOL_SIZE,
    waitForConnections: true,
    // The server enforces require_secure_transport, so the driver must speak TLS — but the
    // connection rides an SSM tunnel to 127.0.0.1, so the server certificate can never match
    // the hostname. The tunnel itself is the authenticated channel here.
    ssl: { rejectUnauthorized: false },
  })
  return pool
}

export async function sql(statement: string, params: unknown[] = []): Promise<Row[]> {
  const [rows] = await db().query(statement, params)
  return Array.isArray(rows) ? (rows as Row[]) : []
}

/**
 * Releases the worker's connections. Pooled sockets keep the event loop alive, so a suite that
 * seeds ends by calling this.
 */
export async function closeSeedPool(): Promise<void> {
  const open = pool
  pool = undefined
  await open?.end()
}

export interface SeededUser {
  id: number
  providerId: string
}

// username_suffix is unique across the table and capped at five characters.
function randomSuffix(): string {
  const alphabet = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'
  return Array.from({ length: 5 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join(
    '',
  )
}

/**
 * Creates an account nothing else refers to, so a suite can delete exactly what it made.
 */
export async function createUser(label: string): Promise<SeededUser> {
  const providerId = `e2e-${label}-${process.pid}-${process.hrtime.bigint()}`
  await sql(
    `INSERT INTO users (public_id, email, provider, provider_id, username_epithet, username_suffix)
     VALUES (UUID_TO_BIN(UUID()), ?, 'google', ?, 'Faust', ?)`,
    [`${providerId}@example.test`, providerId, randomSuffix()],
  )
  const rows = await sql('SELECT id FROM users WHERE provider_id = ?', [providerId])
  return { id: Number(rows[0]!.id), providerId }
}

/**
 * Looks up an account the application itself created, so a login journey can clean up the row it
 * caused the OAuth callback to write.
 */
export async function findUserByEmail(email: string): Promise<SeededUser | null> {
  const rows = await sql('SELECT id, provider_id FROM users WHERE email = ?', [email])
  const row = rows[0]
  return row ? { id: Number(row.id), providerId: String(row.provider_id) } : null
}

// The projection tables carry no foreign key to planner, so the cascade from a user delete leaves
// planner_content, planner_stats, planner_publication and planner_moderation behind.
// UserAccountLifecycleService.performHardDelete owns the same list.
const PLANNER_KEYED_TABLES = [
  'planner_entity_filter',
  'planner_keyword_filter',
  'planner_catalog',
  'planner_stats',
  'planner_moderation',
  'planner_publication',
  'planner_content',
  'planner_views',
  'planner_votes',
  'planner_bookmarks',
  'planner_subscriptions',
  'planner_reports',
  'notifications',
]

const USER_KEYED_TABLES = [
  'planner_comment_votes',
  'planner_votes',
  'planner_bookmarks',
  'planner_subscriptions',
  'notifications',
  'user_settings',
]

// The environment deploys whatever image its GitOps revision names, and that image's Flyway
// chain decides which tables exist — the working tree's migrations may be ahead of it (the
// planner/planners decomposition being the live case). The sweep therefore consults the
// deployed schema instead of assuming this checkout's.
async function existing(tables: string[]): Promise<Set<string>> {
  const rows = await sql(
    `SELECT table_name AS t FROM information_schema.tables
       WHERE table_schema = DATABASE() AND table_name IN (${tables.map(() => '?').join(',')})`,
    tables,
  )
  return new Set(rows.map((r) => String((r as { t: string }).t)))
}

export async function deleteUser(user: SeededUser): Promise<void> {
  const plannerTable = (await existing(['planner', 'planners'])).has('planner')
    ? 'planner'
    : 'planners'
  const owned = `SELECT id FROM ${plannerTable} WHERE user_id = ${user.id}`
  const present = await existing([
    'planner_comment_reports',
    'planner_comments',
    'planner_comment_votes',
    ...PLANNER_KEYED_TABLES,
    ...USER_KEYED_TABLES,
  ])

  if (present.has('planner_comments')) {
    if (present.has('planner_comment_reports')) {
      await sql(
        `DELETE FROM planner_comment_reports WHERE comment_id IN
           (SELECT id FROM planner_comments WHERE planner_id IN (${owned}) OR user_id = ${user.id})`,
      )
    }
    if (present.has('planner_comment_votes')) {
      await sql(
        `DELETE FROM planner_comment_votes WHERE comment_id IN
           (SELECT id FROM planner_comments WHERE planner_id IN (${owned}) OR user_id = ${user.id})`,
      )
    }
    await sql(`DELETE FROM planner_comments WHERE planner_id IN (${owned}) OR user_id = ${user.id}`)
  }

  for (const table of PLANNER_KEYED_TABLES) {
    if (present.has(table)) await sql(`DELETE FROM ${table} WHERE planner_id IN (${owned})`)
  }
  await sql(`DELETE FROM ${plannerTable} WHERE user_id = ${user.id}`)

  for (const table of USER_KEYED_TABLES) {
    if (present.has(table)) await sql(`DELETE FROM ${table} WHERE user_id = ${user.id}`)
  }
  await sql(`DELETE FROM users WHERE id = ${user.id}`)
}
