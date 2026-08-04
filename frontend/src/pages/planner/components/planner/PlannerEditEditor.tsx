import { PlannerEditorShell } from './PlannerEditorShell'
import { usePlannerConfig } from '../../hooks/usePlannerConfig'

import type { PlannerEditorSession } from './PlannerEditorShell'
import type { SaveablePlanner } from '../../types/PlannerTypes'

/**
 * Read an existing planner's editing session.
 *
 * An existing planner keeps the content version it was authored against, so a
 * game update never silently reinterprets its contents. `currentContentVersion`
 * only covers a stored value of 0, which predates the field.
 */
export function editorSessionFor(
  planner: SaveablePlanner,
  currentContentVersion: number,
): PlannerEditorSession {
  return {
    contentVersion: planner.metadata.contentVersion || currentContentVersion,
    initialPlannerId: planner.metadata.id || undefined,
    initialSyncVersion: planner.metadata.syncVersion,
    initialSavedAt: planner.metadata.lastModifiedAt || undefined,
  }
}

interface PlannerEditEditorProps {
  planner: SaveablePlanner
}

/**
 * Editor for a stored planner.
 */
export function PlannerEditEditor({ planner }: PlannerEditEditorProps) {
  const config = usePlannerConfig()

  return <PlannerEditorShell {...editorSessionFor(planner, config.mdCurrentVersion)} />
}
