import { PlannerEditorShell } from './PlannerEditorShell'
import { usePlannerConfig } from '../../hooks/usePlannerConfig'

export function PlannerCreateEditor() {
  const config = usePlannerConfig()

  return <PlannerEditorShell contentVersion={config.mdCurrentVersion} />
}
