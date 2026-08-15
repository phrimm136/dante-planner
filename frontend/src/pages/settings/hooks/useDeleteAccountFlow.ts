import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'

import { showSuccess } from '@/lib/errorPresentation'
import { DATE_FORMATS, formatPlannerDate } from '@/lib/formatDate'
import { I18N_LOCALE_MAP } from '@/lib/constants'
import { authQueryKeys } from '@/shared/auth'
import { useDeleteAccountMutation } from './useAccountData'

const UNKNOWN_DATE_PLACEHOLDER = 'unknown date'

/** Grace period reported when the response carries none. */
const DEFAULT_GRACE_PERIOD_DAYS = 30

/** Time the success toast holds the page before the redirect takes it. */
const REDIRECT_DELAY_MS = 2000

interface DeleteAccountFlow {
  /** Whether the confirmation dialog is up. */
  dialogOpen: boolean
  openDialog: () => void
  closeDialog: () => void
  /** Deletes the account, then arms the redirect the toast is read during. */
  confirmDelete: () => void
  isPending: boolean
}

/**
 * The account-deletion flow: confirmation, the deletion itself, and the delayed
 * redirect that follows it.
 *
 * The redirect is cancelled when the section unmounts, so a user who navigates
 * away during the grace window is not pulled back to the landing page.
 */
export function useDeleteAccountFlow(): DeleteAccountFlow {
  const { i18n } = useTranslation()
  const deleteAccount = useDeleteAccountMutation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [dialogOpen, setDialogOpen] = useState(false)
  const redirectTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    return () => {
      if (redirectTimer.current !== null) {
        clearTimeout(redirectTimer.current)
      }
    }
  }, [])

  const confirmDelete = () => {
    deleteAccount.mutate(undefined, {
      onSuccess: (response) => {
        const formattedDate =
          formatPlannerDate(
            response.permanentDeleteAt,
            I18N_LOCALE_MAP[i18n.language] ?? 'en-US',
            DATE_FORMATS.LONG_DATE,
          ) ?? UNKNOWN_DATE_PLACEHOLDER

        showSuccess('common:settings.deleteAccount.success', {
          date: formattedDate,
          days: response.gracePeriodDays ?? DEFAULT_GRACE_PERIOD_DAYS,
        })

        setDialogOpen(false)

        // Emptying the auth cache is what logs the deleted account out.
        queryClient.setQueryData(authQueryKeys.me, null)

        redirectTimer.current = setTimeout(() => {
          void navigate({ to: '/' })
        }, REDIRECT_DELAY_MS)
      },
    })
  }

  return {
    dialogOpen,
    openDialog: () => {
      setDialogOpen(true)
    },
    closeDialog: () => {
      setDialogOpen(false)
    },
    confirmDelete,
    isPending: deleteAccount.isPending,
  }
}
