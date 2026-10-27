import { useState, startTransition } from 'react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { PlannerStoreProvider } from '../../stores/usePlannerStore'
import { hydrateEditorState } from '../../lib/editorStateCodec'
import type { MDSaveablePlanner } from '../../types/PlannerTypes'

import { GuideModeViewer } from './GuideModeViewer'
import { TrackerModeViewer } from './TrackerModeViewer'

type ViewerMode = 'guide' | 'tracker'

interface PlannerViewerProps {
  planner: MDSaveablePlanner
}

interface PlannerViewerStoreProps {
  planner: MDSaveablePlanner
  children: ReactNode
}

export function PlannerViewerStore({ planner, children }: PlannerViewerStoreProps) {
  const metadata = {
    title: planner.metadata.title,
    category: planner.config.category,
    isPublished: planner.metadata.published ?? false,
  }
  const viewerInitialState = (maxLevel: number) => {
    const { skillEAState: _plan, ...state } = hydrateEditorState(
      planner.content,
      metadata,
      maxLevel,
    )
    return state
  }

  return (
    <PlannerStoreProvider
      key={planner.metadata.id}
      mdVersion={planner.metadata.contentVersion}
      initialState={viewerInitialState}
    >
      {children}
    </PlannerStoreProvider>
  )
}

export function PlannerViewer({ planner }: PlannerViewerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const [mode, setMode] = useState<ViewerMode>('guide')
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
    <PlannerViewerStore planner={planner}>
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

      <div className={cn(mode !== 'guide' && 'hidden')} aria-hidden={mode !== 'guide'}>
        <GuideModeViewer planner={planner} />
      </div>

      {trackerMounted && (
        <div className={cn(mode !== 'tracker' && 'hidden')} aria-hidden={mode !== 'tracker'}>
          <TrackerModeViewer planner={planner} />
        </div>
      )}
    </PlannerViewerStore>
  )
}
