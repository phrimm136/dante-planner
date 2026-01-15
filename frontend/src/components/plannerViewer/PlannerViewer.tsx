import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
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
 * Mode switching preserves tracker state (no reset on mode change).
 * State resets only on page refresh or component unmount.
 *
 * @example
 * <PlannerViewer planner={loadedPlanner} />
 */
export function PlannerViewer({ planner }: PlannerViewerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const [mode, setMode] = useState<ViewerMode>('guide')

  return (
    <div className="container mx-auto">
      <div className="space-y-4">
        <div className="flex justify-center gap-2 pb-4 border-b">
          <Button
            variant={mode === 'guide' ? 'default' : 'outline'}
            onClick={() => setMode('guide')}
          >
            {t('pages.plannerMD.viewer.guideMode')}
          </Button>
          <Button
            variant={mode === 'tracker' ? 'default' : 'outline'}
            onClick={() => setMode('tracker')}
          >
            {t('pages.plannerMD.viewer.trackerMode')}
          </Button>
        </div>

        {mode === 'guide' ? (
          <GuideModeViewer planner={planner} />
        ) : (
          <TrackerModeViewer planner={planner} />
        )}
      </div>
    </div>
  )
}
