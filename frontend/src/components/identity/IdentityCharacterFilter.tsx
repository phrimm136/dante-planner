import { useTranslation } from 'react-i18next'

export function IdentityCharacterFilter() {
  const { t } = useTranslation()

  return (
    <div className="bg-card border border-border rounded-md p-4 h-20 flex items-center justify-center">
      <span className="text-sm font-medium text-muted-foreground">
        {t('pages.identity.characterFilter')}
      </span>
    </div>
  )
}
