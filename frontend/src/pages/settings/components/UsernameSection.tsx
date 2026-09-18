import { useState, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { showSuccess } from '@/lib/errorPresentation'

import { useAuthQuery } from '@/shared/auth'
import { useEpithetsQuery, useUpdateEpithetMutation } from '../hooks/useAccountData'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { startGoogleLogin } from '@/shared/auth'
import { formatUsername } from '@/lib/formatUsername'
import { ChevronDown } from 'lucide-react'
import { GoogleIcon } from '@/components/ui/GoogleIcon'
import { SECTION_STYLES } from '@/lib/constants'

function UsernameSectionContent() {
  const { t, i18n } = useTranslation(['common', 'epithet'])
  const { data: user } = useAuthQuery()
  const { epithets } = useEpithetsQuery()
  const updateEpithet = useUpdateEpithetMutation()

  const [selectedEpithet, setSelectedEpithet] = useState<string | null>(null)

  const effectiveEpithet = selectedEpithet ?? user?.usernameEpithet ?? ''

  const displayName = t(effectiveEpithet, { ns: 'epithet', defaultValue: effectiveEpithet })

  const handleSave = () => {
    if (!selectedEpithet) return

    updateEpithet.mutate(
      { epithet: selectedEpithet },
      {
        onSuccess: () => {
          showSuccess('common:settings.username.saveSuccess')
          setSelectedEpithet(null)
        },
      }
    )
  }

  const isSaveEnabled = selectedEpithet !== null && selectedEpithet !== user?.usernameEpithet

  if (!user) {
    return (
      <div className="space-y-4">
        <h2 className={SECTION_STYLES.TEXT.sectionTitle}>{t('settings.username.title', 'Username')}</h2>
        <p className={SECTION_STYLES.TEXT.muted}>
          {t('settings.username.signInPrompt', 'Sign in to customize your username')}
        </p>
        <Button onClick={startGoogleLogin} className={SECTION_STYLES.LAYOUT.row}>
          <GoogleIcon className="h-4 w-4" />
          {t('header.auth.googleLogin', 'Sign in with Google')}
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <h2 className={SECTION_STYLES.TEXT.sectionTitle}>{t('settings.username.title', 'Username')}</h2>

      <div className={SECTION_STYLES.TEXT.caption}>
        {t('settings.username.current')}: {formatUsername(user.usernameEpithet, user.usernameSuffix, i18n.language)}
      </div>

      <div className="flex items-center gap-4">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" className="w-48 justify-between">
              <span>{displayName}</span>
              <ChevronDown className="size-4 opacity-50" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent className="w-48">
            <DropdownMenuRadioGroup
              value={effectiveEpithet}
              onValueChange={setSelectedEpithet}
            >
              {epithets.map((epithet) => (
                <DropdownMenuRadioItem key={epithet} value={epithet}>
                  {t(epithet, { ns: 'epithet', defaultValue: epithet })}
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuContent>
        </DropdownMenu>

        <Button
          onClick={handleSave}
          disabled={!isSaveEnabled || updateEpithet.isPending}
        >
          {updateEpithet.isPending
            ? t('settings.username.saving', 'Saving...')
            : t('settings.username.save', 'Save')}
        </Button>
      </div>

      {isSaveEnabled && selectedEpithet && (
        <div className="text-sm">
          {t('settings.username.preview')}: {formatUsername(selectedEpithet, user.usernameSuffix, i18n.language)}
        </div>
      )}
    </div>
  )
}

export function UsernameSection() {
  return (
    <Suspense fallback={<UsernameSectionSkeleton />}>
      <UsernameSectionContent />
    </Suspense>
  )
}

function UsernameSectionSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-6 w-24" />
      <Skeleton className="h-4 w-48" />
      <div className="flex items-center gap-4">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-10 w-20" />
      </div>
    </div>
  )
}
