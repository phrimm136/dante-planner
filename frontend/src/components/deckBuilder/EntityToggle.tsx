import { memo } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { EntityMode } from '@/types/DeckTypes'

interface EntityToggleProps {
  mode: EntityMode
  onModeChange: (mode: EntityMode) => void
}

export const EntityToggle = memo(function EntityToggle({ mode, onModeChange }: EntityToggleProps) {
  const { t } = useTranslation('planner')

  return (
    <div className="flex gap-1 p-2 h-14 bg-card border border-border rounded-md">
      <Button
        variant="ghost"
        size="sm"
        className={cn(
          'flex-1 h-full transition-colors',
          mode === 'identity' && 'bg-background shadow-sm'
        )}
        onClick={() => { onModeChange('identity') }}
      >
        {t('deckBuilder.entityToggle.identity')}
      </Button>
      <Button
        variant="ghost"
        size="sm"
        className={cn(
          'flex-1 h-full transition-colors',
          mode === 'ego' && 'bg-background shadow-sm'
        )}
        onClick={() => { onModeChange('ego') }}
      >
        {t('deckBuilder.entityToggle.ego')}
      </Button>
    </div>
  )
}, (prev, next) => {
  return prev.mode === next.mode
  // onModeChange excluded - callback identity changes but behavior is same
})
