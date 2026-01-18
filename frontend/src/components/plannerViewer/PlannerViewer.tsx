import { useState, startTransition } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { SaveablePlanner } from '@/types/PlannerTypes'

import { GuideModeViewer } from './GuideModeViewer'
import { TrackerModeViewer } from './TrackerModeViewer'

type ViewerMode = 'guide' | 'tracker'

interface PlannerViewerProps {
  planner: SaveablePlanner
}

/**
 * Container component for planner viewer with mode switching.
 * Manages mode state (guide ↔ tracker) and routes to appropriate viewer.
 *
 * Uses lazy mounting with CSS visibility toggle:
 * - TrackerModeViewer only mounts when first accessed (prevents initial render freeze)
 * - Once mounted, both viewers stay mounted for instant switching
 * - Preserves tracker state across mode changes
 *
 * State resets only on page refresh or component unmount.
 *
 * @example
 * <PlannerViewer planner={loadedPlanner} />
 */
export function PlannerViewer({ planner }: PlannerViewerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const [mode, setMode] = useState<ViewerMode>('guide')
  // Track if tracker has ever been accessed - once true, stays mounted for instant switching
  const [trackerMounted, setTrackerMounted] = useState(false)

  const handleModeChange = (newMode: ViewerMode) => {
    if (newMode === 'tracker' && !trackerMounted) {
      setTrackerMounted(true)
    }
    startTransition(() => {
      setMode(newMode)
    })
  }

  return (
    <>
      <div className="flex justify-center gap-2 pb-4 border-b">
        <Button
          variant={mode === 'guide' ? 'default' : 'outline'}
          onClick={() => handleModeChange('guide')}
          aria-pressed={mode === 'guide'}
        >
          {t('pages.plannerMD.viewer.guideMode')}
        </Button>
        <Button
          variant={mode === 'tracker' ? 'default' : 'outline'}
          onClick={() => handleModeChange('tracker')}
          aria-pressed={mode === 'tracker'}
        >
          {t('pages.plannerMD.viewer.trackerMode')}
        </Button>
      </div>

      {/* Guide mode - always mounted */}
      <div
        className={cn(mode !== 'guide' && 'hidden')}
        aria-hidden={mode !== 'guide'}
      >
        <GuideModeViewer planner={planner} />
      </div>

      {/* Tracker mode - lazy mounted on first access, then stays mounted */}
      {trackerMounted && (
        <div
          className={cn(mode !== 'tracker' && 'hidden')}
          aria-hidden={mode !== 'tracker'}
        >
          <TrackerModeViewer planner={planner} />
        </div>
      )}
    </>
  )
}
