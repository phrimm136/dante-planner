import { execFileSync } from 'node:child_process'

// Accounts are created only by the OAuth callback. This reaches the database through the compose
// container, so it works against the local stack alone.
const MYSQL_CONTAINER = process.env.E2E_MYSQL_CONTAINER ?? 'limbusplanner-mysql-1'
const MYSQL_DATABASE = process.env.E2E_MYSQL_DATABASE ?? 'danteplanner'
const MYSQL_USER = process.env.E2E_MYSQL_USER ?? 'danteplanner'

export function sql(statement: string): string {
  return execFileSync(
    'docker',
    [
      'exec',
      MYSQL_CONTAINER,
      'sh',
      '-c',
      `mysql -u ${MYSQL_USER} -p"$MYSQL_PASSWORD" ${MYSQL_DATABASE} -N -B -e ${JSON.stringify(statement)}`,
    ],
    { encoding: 'utf8' },
  ).trim()
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
export function createUser(label: string): SeededUser {
  const providerId = `e2e-${label}-${process.pid}-${process.hrtime.bigint()}`
  sql(
    `INSERT INTO users (public_id, email, provider, provider_id, username_epithet, username_suffix)
     VALUES (UUID_TO_BIN(UUID()), '${providerId}@example.test', 'google', '${providerId}', 'Faust', '${randomSuffix()}')`,
  )
  const id = Number(sql(`SELECT id FROM users WHERE provider_id = '${providerId}'`))
  return { id, providerId }
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

export function deleteUser(user: SeededUser): void {
  const owned = `SELECT id FROM planner WHERE user_id = ${user.id}`

  sql(
    `DELETE FROM planner_comment_reports WHERE comment_id IN
       (SELECT id FROM planner_comments WHERE planner_id IN (${owned}) OR user_id = ${user.id})`,
  )
  sql(
    `DELETE FROM planner_comment_votes WHERE comment_id IN
       (SELECT id FROM planner_comments WHERE planner_id IN (${owned}) OR user_id = ${user.id})`,
  )
  sql(`DELETE FROM planner_comments WHERE planner_id IN (${owned}) OR user_id = ${user.id}`)

  for (const table of PLANNER_KEYED_TABLES) {
    sql(`DELETE FROM ${table} WHERE planner_id IN (${owned})`)
  }
  sql(`DELETE FROM planner WHERE user_id = ${user.id}`)

  for (const table of USER_KEYED_TABLES) {
    sql(`DELETE FROM ${table} WHERE user_id = ${user.id}`)
  }
  sql(`DELETE FROM users WHERE id = ${user.id}`)
}
