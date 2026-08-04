import { PLANNER_CONFIG, PLANNER_STORAGE_KEYS } from '@/lib/constants'
import type { Page } from '@playwright/test'
import { localPlannerContent } from './plannerContent'

const DB_NAME = 'danteplanner'
const STORE_NAME = 'planner'
const DEVICE_ID = 'e2e-device'
const SCHEMA_VERSION = 1
const SYNC_VERSION = 1
const DB_VERSION = 1

/**
 * The planner editor reads only IndexedDB.
 *
 * `useSavedPlannerQuery` calls `loadFromLocal`, and there is no server fallback on that route, so
 * a planner created through the API alone renders the not-found page. Anything driving
 * `/planner/md/$id/edit` has to put the row in the browser first.
 */
export interface LocalPlannerSeed {
  plannerId: string
  title: string
  published?: boolean
}

export async function seedLocalPlanner(page: Page, seed: LocalPlannerSeed): Promise<void> {
  const { plannerId, title, published = false } = seed
  const timestamp = new Date().toISOString()

  const planner = {
    metadata: {
      id: plannerId,
      title,
      status: 'saved',
      schemaVersion: SCHEMA_VERSION,
      contentVersion: PLANNER_CONFIG.mdCurrentVersion,
      plannerType: 'MIRROR_DUNGEON',
      syncVersion: SYNC_VERSION,
      createdAt: timestamp,
      lastModifiedAt: timestamp,
      savedAt: timestamp,
      deviceId: DEVICE_ID,
      published,
    },
    config: { type: 'MIRROR_DUNGEON', category: '5F' },
    content: localPlannerContent(),
  }

  const key = [PLANNER_STORAGE_KEYS.PLANNER, PLANNER_STORAGE_KEYS.MD, DEVICE_ID, plannerId].join(
    ':',
  )

  await page.addInitScript(
    (script: {
      dbName: string
      dbVersion: number
      store: string
      deviceIdKey: string
      deviceId: string
      key: string
      value: string
    }) => {
      const request = indexedDB.open(script.dbName, script.dbVersion)
      request.onupgradeneeded = () => {
        if (!request.result.objectStoreNames.contains(script.store)) {
          request.result.createObjectStore(script.store)
        }
      }
      request.onsuccess = () => {
        const store = request.result.transaction(script.store, 'readwrite').objectStore(script.store)
        store.put(script.deviceId, script.deviceIdKey)
        store.put(script.value, script.key)
      }
    },
    {
      dbName: DB_NAME,
      dbVersion: DB_VERSION,
      store: STORE_NAME,
      deviceIdKey: PLANNER_STORAGE_KEYS.DEVICE_ID,
      deviceId: DEVICE_ID,
      key,
      value: JSON.stringify(planner),
    },
  )
}
