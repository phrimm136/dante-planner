import { Circle, CheckCircle, CloudUpload, Globe, AlertCircle } from 'lucide-react'
import { cn } from '@/lib/utils'

import type { LucideIcon } from 'lucide-react'
import type { SaveStatus } from '../../lib/plannerBadges'

interface PlannerStatusIconProps {
  status: SaveStatus
  className?: string
}

interface StatusIconSpec {
  Icon: LucideIcon
  className: string
  label: string
}

/**
 * Icon, colour and accessible name per save status. Total over `SaveStatus`, so
 * a new status cannot ship without one.
 */
const STATUS_ICONS: Record<SaveStatus, StatusIconSpec> = {
  draft: {
    Icon: Circle,
    className: 'text-yellow-600 dark:text-yellow-400',
    label: 'Draft',
  },
  saved: {
    Icon: Circle,
    className: 'fill-current text-muted-foreground',
    label: 'Saved',
  },
  unsynced: {
    Icon: CloudUpload,
    className: 'text-blue-600 dark:text-blue-400',
    label: 'Unsynced',
  },
  synced: {
    Icon: CheckCircle,
    className: 'text-primary',
    label: 'Synced',
  },
  published: {
    Icon: Globe,
    className: 'text-primary',
    label: 'Published',
  },
  unpublishedChanges: {
    Icon: AlertCircle,
    className: 'text-orange-600 dark:text-orange-400',
    label: 'Unpublished changes',
  },
}

/**
 * Symbol indicator for planner status (used in cards).
 * Displays minimal icon without text for compact representation.
 */
export function PlannerStatusIcon({ status, className }: PlannerStatusIconProps) {
  const { Icon, className: statusClass, label } = STATUS_ICONS[status]

  return <Icon className={cn('size-4', className, statusClass)} aria-label={label} />
}
