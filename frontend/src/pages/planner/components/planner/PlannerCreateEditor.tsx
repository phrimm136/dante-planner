import { PlannerEditorShell } from './PlannerEditorShell'
import { usePlannerConfig } from '../../hooks/usePlannerConfig'

/**
 * Editor for a planner that does not exist yet: no id, no server version, and
 * the current game content version.
 */
export function PlannerCreateEditor() {
  const config = usePlannerConfig()

  return <PlannerEditorShell contentVersion={config.mdCurrentVersion} />
}
