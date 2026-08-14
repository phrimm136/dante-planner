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
import { showSuccess } from '@/lib/errorPresentation'

import type { ModerationDialogProps } from '@/shared/moderation'
import type { UserForMod, ModerationAction, ModerationDialogKind } from './types/ModeratorTypes'
import { SECTION_STYLES, STATUS_TEXT_COLORS } from '@/lib/constants'

const ROLE_TEXT_STYLES: Record<UserForMod['role'], string> = {
  ADMIN: `${STATUS_TEXT_COLORS.DANGER} font-semibold`,
  MODERATOR: `${STATUS_TEXT_COLORS.INFO} font-semibold`,
  NORMAL: '',
}

const ACTION_TEXT_STYLES: Record<ModerationAction['actionType'], string> = {
  BAN: STATUS_TEXT_COLORS.DANGER,
  UNBAN: STATUS_TEXT_COLORS.SUCCESS,
  TIMEOUT: STATUS_TEXT_COLORS.WARNING,
  CLEAR_TIMEOUT: STATUS_TEXT_COLORS.SUCCESS,
  PROMOTE: STATUS_TEXT_COLORS.INFO,
  DEMOTE: STATUS_TEXT_COLORS.WARNING,
  DELETE_PLANNER: STATUS_TEXT_COLORS.DANGER,
  DELETE_COMMENT: STATUS_TEXT_COLORS.DANGER,
  UNPUBLISH_PLANNER: STATUS_TEXT_COLORS.WARNING,
  HIDE_FROM_RECOMMENDED: STATUS_TEXT_COLORS.WARNING,
  UNHIDE_FROM_RECOMMENDED: STATUS_TEXT_COLORS.SUCCESS,
}

const TABLE_HEADER_CLASS = 'px-4 py-3 text-left text-sm font-semibold'

const USER_TABLE_HEADER_KEYS = [
  'dashboard.username',
  'dashboard.role',
  'dashboard.status',
  'dashboard.actions',
]

const HISTORY_TABLE_HEADER_KEYS = [
  'dashboard.time',
  'dashboard.action',
  'dashboard.targetType',
  'dashboard.moderator',
  'dashboard.reason',
  'dashboard.duration',
]

function TableHeaderRow({ labelKeys }: { labelKeys: readonly string[] }) {
  const { t } = useTranslation(['moderation'])

  return (
    <tr>
      {labelKeys.map((key) => (
        <th key={key} className={TABLE_HEADER_CLASS}>
          {t(key)}
        </th>
      ))}
    </tr>
  )
}

/** What one user-row dialog renders and which toast its mutation resolves to. */
interface ModerationDialogSpec {
  Dialog: (props: ModerationDialogProps) => React.ReactNode
  successKey: string
}

const MODERATION_DIALOGS: Record<ModerationDialogKind, ModerationDialogSpec> = {
  ban: {
    Dialog: BanDialog,
    successKey: 'moderation:dashboard.userBanned',
  },
  unban: {
    Dialog: UnbanDialog,
    successKey: 'moderation:dashboard.userUnbanned',
  },
  timeout: {
    Dialog: TimeoutDialog,
    successKey: 'moderation:dashboard.userTimedOut',
  },
  clearTimeout: {
    Dialog: ClearTimeoutDialog,
    successKey: 'moderation:dashboard.timeoutRemoved',
  },
}

const MODERATION_DIALOG_KINDS = Object.keys(MODERATION_DIALOGS) as ModerationDialogKind[]

/** Widest variable shape across the four mutations; a duration is ignored where unused. */
interface ModerationVariables {
  usernameSuffix: string
  reason: string
  durationMinutes: number
}

/** The slice of a mutation result the dialog table needs. */
interface ModerationMutation {
  mutate: (variables: ModerationVariables, options: { onSuccess: () => void }) => void
  isPending: boolean
}

/**
 * User table row with action buttons
 */
function UserRow({ user, currentUserSuffix }: { user: UserForMod; currentUserSuffix: string }) {
  const { t, i18n } = useTranslation(['moderation', 'common'])
  const [openDialog, setOpenDialog] = useState<ModerationDialogKind | null>(null)

  const mutations: Record<ModerationDialogKind, ModerationMutation> = {
    ban: useBanUser(),
    unban: useUnbanUser(),
    timeout: useTimeoutUser(),
    clearTimeout: useUntimeoutUser(),
  }

  const isSelf = user.usernameSuffix === currentUserSuffix
  const canBan = user.role !== 'ADMIN' && !isSelf
  const canTimeout = (user.role === 'NORMAL' || user.role === 'MODERATOR') && !isSelf
  const username = formatUsername(user.usernameEpithet, user.usernameSuffix, i18n.language)

  const confirmModeration =
    (kind: ModerationDialogKind) => (reason: string, durationMinutes?: number) => {
      const { successKey } = MODERATION_DIALOGS[kind]
      mutations[kind].mutate(
        { usernameSuffix: user.usernameSuffix, reason, durationMinutes: durationMinutes ?? 0 },
        {
          onSuccess: () => {
            showSuccess(successKey)
            setOpenDialog(null)
          },
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
        {user.isBanned && <span className={STATUS_TEXT_COLORS.DANGER}>Banned</span>}
        {user.isTimedOut && <span className={STATUS_TEXT_COLORS.WARNING}>Timed Out</span>}
        {!user.isBanned && !user.isTimedOut && <span className={SECTION_STYLES.TEXT.muted}>-</span>}
      </td>
      <td className="px-4 py-3">
        <div className={SECTION_STYLES.LAYOUT.rowTight}>
          {user.isBanned ? (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setOpenDialog('unban')}
              disabled={mutations.unban.isPending || isSelf}
            >
              <UserCheck className="size-4 mr-1" />
              {t('dashboard.unban')}
            </Button>
          ) : (
            <Button
              variant="destructive"
              size="sm"
              onClick={() => setOpenDialog('ban')}
              disabled={!canBan || mutations.ban.isPending}
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
              disabled={mutations.clearTimeout.isPending || isSelf}
            >
              <UserCheck className="size-4 mr-1" />
              {t('dashboard.untimeout')}
            </Button>
          ) : (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setOpenDialog('timeout')}
              disabled={!canTimeout || mutations.timeout.isPending}
            >
              <Clock className="size-4 mr-1" />
              {t('dashboard.timeout')}
            </Button>
          )}
        </div>
      </td>

      {/* Dialogs */}
      {MODERATION_DIALOG_KINDS.map((kind) => {
        const { Dialog } = MODERATION_DIALOGS[kind]
        return (
          <Dialog
            key={kind}
            open={openDialog === kind}
            onOpenChange={(next) => setOpenDialog(next ? kind : null)}
            username={username}
            onConfirm={confirmModeration(kind)}
            isPending={mutations[kind].isPending}
          />
        )
      })}
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
              <TableHeaderRow labelKeys={USER_TABLE_HEADER_KEYS} />
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
              <TableHeaderRow labelKeys={HISTORY_TABLE_HEADER_KEYS} />
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
