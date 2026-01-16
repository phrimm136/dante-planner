import { memo } from 'react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { EntityMode } from '@/types/DeckTypes'

interface EntityToggleProps {
  mode: EntityMode
  onModeChange: (mode: EntityMode) => void
}

export const EntityToggle = memo(function EntityToggle({ mode, onModeChange }: EntityToggleProps) {
  return (
    <div className="flex gap-1 p-1 bg-muted rounded-lg">
      <Button
        variant="ghost"
        size="sm"
        className={cn(
          'flex-1 transition-colors',
          mode === 'identity' && 'bg-background shadow-sm'
        )}
        onClick={() => { onModeChange('identity') }}
      >
        Identity
      </Button>
      <Button
        variant="ghost"
        size="sm"
        className={cn(
          'flex-1 transition-colors',
          mode === 'ego' && 'bg-background shadow-sm'
        )}
        onClick={() => { onModeChange('ego') }}
      >
        EGO
      </Button>
    </div>
  )
}, (prev, next) => {
  return prev.mode === next.mode
  // onModeChange excluded - callback identity changes but behavior is same
})
