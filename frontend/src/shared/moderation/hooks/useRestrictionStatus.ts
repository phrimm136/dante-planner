import { useAuthQuery } from '@/shared/auth'

export interface RestrictionStatus {
  /** Banned or timed out; either one disables the write paths */
  isRestricted: boolean
  /** Distinguishes the two, for callers whose copy differs per kind */
  isBanned: boolean
  /** The moderator's reason, absent when none was recorded */
  reason: string | undefined
}

/**
 * The signed-in account's moderation standing.
 *
 * The reason is returned raw so each surface can choose its own wording
 * for the case where no reason was recorded.
 */
export function useRestrictionStatus(): RestrictionStatus {
  const { data: user } = useAuthQuery()

  const isBanned = user?.isBanned === true
  const isRestricted = isBanned || user?.isTimedOut === true

  return {
    isRestricted,
    isBanned,
    reason: isBanned ? user?.banReason : user?.timeoutReason,
  }
}
