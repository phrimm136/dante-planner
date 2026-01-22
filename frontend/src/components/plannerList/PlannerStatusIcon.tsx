import { Circle, CheckCircle, CloudUpload, Globe, AlertCircle } from 'lucide-react'
import { cn } from '@/lib/utils'

type PlannerStatus = 'draft' | 'saved' | 'unsynced' | 'synced' | 'published' | 'unpublishedChanges'

interface PlannerStatusIconProps {
  status: PlannerStatus
  className?: string
}

/**
 * Symbol indicator for planner status (used in cards).
 * Displays minimal icon without text for compact representation.
 *
 * States:
 * - draft: Empty circle (○) - Never manually saved
 * - saved: Filled circle (●) - Saved locally, sync disabled
 * - unsynced: Cloud upload icon - Has unsaved changes (sync enabled)
 * - synced: Check circle - Synced to server
 * - published: Globe - Published to community
 * - unpublishedChanges: Alert - Published but has local changes
 */
export function PlannerStatusIcon({ status, className }: PlannerStatusIconProps) {
  const iconClass = cn('size-4', className)

  switch (status) {
    case 'draft':
      return (
        <Circle
          className={cn(iconClass, 'text-yellow-600 dark:text-yellow-400')}
          aria-label="Draft"
        />
      )
    case 'saved':
      return (
        <Circle
          className={cn(iconClass, 'fill-current text-muted-foreground')}
          aria-label="Saved"
        />
      )
    case 'unsynced':
      return (
        <CloudUpload
          className={cn(iconClass, 'text-blue-600 dark:text-blue-400')}
          aria-label="Unsynced"
        />
      )
    case 'synced':
      return (
        <CheckCircle
          className={cn(iconClass, 'text-primary')}
          aria-label="Synced"
        />
      )
    case 'published':
      return (
        <Globe
          className={cn(iconClass, 'text-primary')}
          aria-label="Published"
        />
      )
    case 'unpublishedChanges':
      return (
        <AlertCircle
          className={cn(iconClass, 'text-orange-600 dark:text-orange-400')}
          aria-label="Unpublished changes"
        />
      )
  }
}
