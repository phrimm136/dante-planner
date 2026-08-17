import i18n from '@/lib/i18n'
import { storage } from '@/lib/storage'
import { storageKeys } from '../hooks/usePlannerStorage'

/** Title shown for a planner that carries none of its own. */
export const untitledPlannerTitle = (): string =>
  i18n.t('pages.plannerMD.untitled', { ns: 'planner' })

/** Title for a planner route's head, read from local storage. */
export async function loadPlannerTitle(plannerId: string): Promise<string> {
  const rawData = await storage.getItem(storageKeys.planner(plannerId))
  if (!rawData.ok) {
    console.error('Planner title read could not reach storage:', rawData.error)
    return untitledPlannerTitle()
  }
  if (rawData.value === null) return untitledPlannerTitle()

  try {
    const parsed = JSON.parse(rawData.value)
    return parsed?.metadata?.title || untitledPlannerTitle()
  } catch {
    return untitledPlannerTitle()
  }
}
