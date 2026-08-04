import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Shield, Ban, Clock, UserCheck } from 'lucide-react'

import { useAuthQuery, isStaff } from '@/shared/auth'
import { useModeratorUsers, useModerationHistory } from './hooks/useModeratorData'
import {
  useBanUser,
  useUnbanUser,
  useTimeoutUser,
  useUntimeoutUser,
} from './hooks/useModeratorMutations'
import { BanDialog, TimeoutDialog, UnbanDialog, ClearTimeoutDialog } from '@/shared/moderation'
import { Button } from '@/components/ui/button'
import { formatUsername } from '@/lib/formatUsername'
import { formatRelativeTime } from '@/lib/formatDate'
import { toast } from '@/lib/toast'

import type { UserForMod, ModerationAction, ModerationDialogKind } from './types/ModeratorTypes'
import { SECTION_STYLES } from '@/lib/constants'

const ROLE_TEXT_STYLES: Record<UserForMod['role'], string> = {
  ADMIN: 'text-red-500 font-semibold',
  MODERATOR: 'text-blue-500 font-semibold',
  NORMAL: '',
}

const ACTION_TEXT_STYLES: Record<ModerationAction['actionType'], string> = {
  BAN: 'text-red-500',
  UNBAN: 'text-green-500',
  TIMEOUT: 'text-orange-500',
  CLEAR_TIMEOUT: 'text-green-500',
  PROMOTE: 'text-blue-500',
  DEMOTE: 'text-orange-500',
  DELETE_PLANNER: 'text-red-500',
  DELETE_COMMENT: 'text-red-500',
  UNPUBLISH_PLANNER: 'text-orange-500',
  HIDE_FROM_RECOMMENDED: 'text-orange-500',
  UNHIDE_FROM_RECOMMENDED: 'text-green-500',
}

/**
 * User table row with action buttons
 */
function UserRow({ user, currentUserSuffix }: { user: UserForMod; currentUserSuffix: string }) {
  const { t, i18n } = useTranslation(['moderation', 'common'])
  const [openDialog, setOpenDialog] = useState<ModerationDialogKind | null>(null)

  const banMutation = useBanUser()
  const unbanMutation = useUnbanUser()
  const timeoutMutation = useTimeoutUser()
  const untimeoutMutation = useUntimeoutUser()

  const isSelf = user.usernameSuffix === currentUserSuffix
  const canBan = user.role !== 'ADMIN' && !isSelf
  const canTimeout = (user.role === 'NORMAL' || user.role === 'MODERATOR') && !isSelf
  const username = formatUsername(user.usernameEpithet, user.usernameSuffix, i18n.language)

  const handleBanConfirm = (reason: string) => {
    banMutation.mutate(
      { usernameSuffix: user.usernameSuffix, reason },
      {
        onSuccess: () => {
          toast.success(t('dashboard.userBanned'))
          setOpenDialog(null)
        },
        onError: () => toast.error(t('dashboard.banFailed')),
      },
    )
  }

  const handleUnbanConfirm = (reason: string) => {
    unbanMutation.mutate(
      { usernameSuffix: user.usernameSuffix, reason },
      {
        onSuccess: () => {
          toast.success(t('dashboard.userUnbanned'))
          setOpenDialog(null)
        },
        onError: () => toast.error(t('dashboard.unbanFailed')),
      },
    )
  }

  const handleTimeoutConfirm = (durationMinutes: number, reason: string) => {
    timeoutMutation.mutate(
      { usernameSuffix: user.usernameSuffix, durationMinutes, reason },
      {
        onSuccess: () => {
          toast.success(t('dashboard.userTimedOut'))
          setOpenDialog(null)
        },
        onError: () => toast.error(t('dashboard.timeoutFailed')),
      },
    )
  }

  const handleUntimeoutConfirm = (reason: string) => {
    untimeoutMutation.mutate(
      { usernameSuffix: user.usernameSuffix, reason },
      {
        onSuccess: () => {
          toast.success(t('dashboard.timeoutRemoved'))
          setOpenDialog(null)
        },
        onError: () => toast.error(t('dashboard.untimeoutFailed')),
      },
    )
  }

  return (
    <tr className="border-b">
      <td className="px-4 py-3 text-sm">
        {formatUsername(user.usernameEpithet, user.usernameSuffix, i18n.language)}
      </td>
      <td className="px-4 py-3 text-sm">
        <span className={ROLE_TEXT_STYLES[user.role]}>{user.role}</span>
      </td>
      <td className="px-4 py-3 text-sm">
        {user.isBanned && <span className="text-red-500">Banned</span>}
        {user.isTimedOut && <span className="text-orange-500">Timed Out</span>}
        {!user.isBanned && !user.isTimedOut && <span className={SECTION_STYLES.TEXT.muted}>-</span>}
      </td>
      <td className="px-4 py-3">
        <div className={SECTION_STYLES.LAYOUT.rowTight}>
          {user.isBanned ? (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setOpenDialog('unban')}
              disabled={unbanMutation.isPending || isSelf}
            >
              <UserCheck className="size-4 mr-1" />
              {t('dashboard.unban')}
            </Button>
          ) : (
            <Button
              variant="destructive"
              size="sm"
              onClick={() => setOpenDialog('ban')}
              disabled={!canBan || banMutation.isPending}
            >
              <Ban className="size-4 mr-1" />
              {t('dashboard.ban')}
            </Button>
          )}

          {user.isTimedOut ? (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setOpenDialog('clearTimeout')}
              disabled={untimeoutMutation.isPending || isSelf}
            >
              <UserCheck className="size-4 mr-1" />
              {t('dashboard.untimeout')}
            </Button>
          ) : (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setOpenDialog('timeout')}
              disabled={!canTimeout || timeoutMutation.isPending}
            >
              <Clock className="size-4 mr-1" />
              {t('dashboard.timeout')}
            </Button>
          )}
        </div>
      </td>

      {/* Dialogs */}
      <BanDialog
        open={openDialog === 'ban'}
        onOpenChange={(next) => setOpenDialog(next ? 'ban' : null)}
        username={username}
        onConfirm={handleBanConfirm}
        isPending={banMutation.isPending}
      />

      <UnbanDialog
        open={openDialog === 'unban'}
        onOpenChange={(next) => setOpenDialog(next ? 'unban' : null)}
        username={username}
        onConfirm={handleUnbanConfirm}
        isPending={unbanMutation.isPending}
      />

      <TimeoutDialog
        open={openDialog === 'timeout'}
        onOpenChange={(next) => setOpenDialog(next ? 'timeout' : null)}
        username={username}
        onConfirm={handleTimeoutConfirm}
        isPending={timeoutMutation.isPending}
      />

      <ClearTimeoutDialog
        open={openDialog === 'clearTimeout'}
        onOpenChange={(next) => setOpenDialog(next ? 'clearTimeout' : null)}
        username={username}
        onConfirm={handleUntimeoutConfirm}
        isPending={untimeoutMutation.isPending}
      />
    </tr>
  )
}

/**
 * Moderation history row
 */
function HistoryRow({ action }: { action: ModerationAction }) {
  const { i18n } = useTranslation()
  const actorName = formatUsername(
    action.actorUsernameEpithet,
    action.actorUsernameSuffix,
    i18n.language,
  )

  return (
    <tr className="border-b text-sm">
      <td className="px-4 py-3">{formatRelativeTime(action.createdAt, i18n.language)}</td>
      <td className="px-4 py-3">
        <span className={`font-semibold ${ACTION_TEXT_STYLES[action.actionType]}`}>
          {action.actionType}
        </span>
      </td>
      <td className="px-4 py-3 text-muted-foreground">{action.targetType}</td>
      <td className="px-4 py-3">{actorName}</td>
      <td className="px-4 py-3 text-muted-foreground max-w-md truncate">{action.reason || '-'}</td>
      <td className="px-4 py-3 text-muted-foreground">
        {action.durationMinutes > 0 ? `${action.durationMinutes}min` : '-'}
      </td>
    </tr>
  )
}

/**
 * Moderator Dashboard Page
 *
 * Displays user list with ban/timeout controls and moderation action history.
 * Only accessible to MODERATOR and ADMIN roles.
 */
export default function ModeratorPage() {
  const { t } = useTranslation(['moderation'])
  const { data: currentUser } = useAuthQuery()
  const users = useModeratorUsers()
  const history = useModerationHistory()

  // Verify user has moderator role
  const isModerator = isStaff(currentUser?.role)
  if (!isModerator) {
    return (
      <div className="container mx-auto px-4 py-8">
        <div className="text-center">
          <Shield className="size-16 mx-auto text-muted-foreground mb-4" />
          <h1 className="text-2xl font-bold mb-2">{t('dashboard.accessDenied')}</h1>
          <p className={SECTION_STYLES.TEXT.muted}>{t('dashboard.moderatorOnly')}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8 space-y-8">
      {/* User List */}
      <section className="space-y-4">
        <h2 className={SECTION_STYLES.TEXT.pageTitle}>{t('dashboard.userManagement')}</h2>
        <div className="border rounded-lg overflow-hidden">
          <table className="w-full">
            <thead className="bg-muted">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-semibold">
                  {t('dashboard.username')}
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold">{t('dashboard.role')}</th>
                <th className="px-4 py-3 text-left text-sm font-semibold">
                  {t('dashboard.status')}
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold">
                  {t('dashboard.actions')}
                </th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <UserRow
                  key={user.usernameSuffix}
                  user={user}
                  currentUserSuffix={currentUser?.usernameSuffix || ''}
                />
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Moderation History */}
      <section className="space-y-4">
        <h2 className={SECTION_STYLES.TEXT.pageTitle}>{t('dashboard.moderationHistory')}</h2>
        <div className="border rounded-lg overflow-hidden">
          <table className="w-full">
            <thead className="bg-muted">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-semibold">{t('dashboard.time')}</th>
                <th className="px-4 py-3 text-left text-sm font-semibold">
                  {t('dashboard.action')}
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold">
                  {t('dashboard.targetType')}
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold">
                  {t('dashboard.moderator')}
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold">
                  {t('dashboard.reason')}
                </th>
                <th className="px-4 py-3 text-left text-sm font-semibold">
                  {t('dashboard.duration')}
                </th>
              </tr>
            </thead>
            <tbody>
              {history.map((action, idx) => (
                <HistoryRow key={idx} action={action} />
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
