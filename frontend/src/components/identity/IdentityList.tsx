import { useTranslation } from 'react-i18next'

export function IdentityList() {
  const { t } = useTranslation()

  return (
    <div className="bg-muted border border-border rounded-md p-6 min-h-96 flex items-center justify-center">
      <span className="text-sm font-medium text-muted-foreground">
        {t('pages.identity.list')}
      </span>
    </div>
  )
}
