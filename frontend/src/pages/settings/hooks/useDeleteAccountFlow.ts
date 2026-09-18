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

const DEFAULT_GRACE_PERIOD_DAYS = 30

const REDIRECT_DELAY_MS = 2000

interface DeleteAccountFlow {
  dialogOpen: boolean
  openDialog: () => void
  closeDialog: () => void
  confirmDelete: () => void
  isPending: boolean
}

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
