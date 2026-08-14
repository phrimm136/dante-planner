import i18n from '@/lib/i18n'
import { storage } from '@/lib/storage'
import { storageKeys } from '../hooks/usePlannerStorage'

/** Title shown for a planner that carries none of its own. */
export const untitledPlannerTitle = (): string =>
  i18n.t('pages.plannerMD.untitled', { ns: 'planner' })

/**
 * Title for a planner route's head, read from local storage.
 *
 * The device id is read rather than minted: a route head runs before the user
 * has done anything, and minting there would create an identity for a visitor
 * who has never saved a planner.
 */
export async function loadPlannerTitle(plannerId: string): Promise<string> {
  const deviceId = await storage.getItem(storageKeys.deviceId())
  if (!deviceId.ok) {
    console.error('Planner title read could not reach storage:', deviceId.error)
    return untitledPlannerTitle()
  }
  if (deviceId.value === null) return untitledPlannerTitle()

  const rawData = await storage.getItem(storageKeys.md(deviceId.value, plannerId))
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
