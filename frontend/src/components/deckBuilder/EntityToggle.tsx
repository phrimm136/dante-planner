import React, { memo } from 'react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type EntityMode = 'identity' | 'ego'

interface EntityToggleProps {
  mode: EntityMode
  onModeChange: (mode: EntityMode) => void
}

export const EntityToggle: React.FC<EntityToggleProps> = memo(({ mode, onModeChange }) => {
  return (
    <div className="flex gap-1 p-1 bg-muted rounded-lg">
      <Button
        variant="ghost"
        size="sm"
        className={cn(
          'flex-1 transition-colors',
          mode === 'identity' && 'bg-background shadow-sm'
        )}
        onClick={() => onModeChange('identity')}
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
        onClick={() => onModeChange('ego')}
      >
        EGO
      </Button>
    </div>
  )
})

export type { EntityMode }
export default EntityToggle
