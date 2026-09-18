import { useAuthQuery } from '@/shared/auth'

export interface RestrictionStatus {
  isRestricted: boolean
  isBanned: boolean
  reason: string | undefined
}

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
