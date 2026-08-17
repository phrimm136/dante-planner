import { PLANNER_CONFIG, PLANNER_STORAGE_KEYS } from '@/lib/constants'
import { DB_NAME, DB_VERSION, STORAGE_STORE_NAME } from '@/lib/storage'
import type { Page } from '@playwright/test'
import { localPlannerContent } from './plannerContent'

const DEVICE_ID = 'e2e-device'
const SCHEMA_VERSION = 1
const SYNC_VERSION = 1

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
  /** 'draft' arranges the pull-side conflict: a background sync must ask, never overwrite. */
  status?: 'saved' | 'draft'
  /** An older game-data version arranges the apply-latest-mirror prompt. */
  contentVersion?: number
}

export async function seedLocalPlanner(page: Page, seed: LocalPlannerSeed): Promise<void> {
  const {
    plannerId,
    title,
    published = false,
    status = 'saved',
    contentVersion = PLANNER_CONFIG.mdCurrentVersion,
  } = seed
  const timestamp = new Date().toISOString()

  const planner = {
    metadata: {
      id: plannerId,
      title,
      status,
      schemaVersion: SCHEMA_VERSION,
      contentVersion,
      plannerType: 'MIRROR_DUNGEON',
      syncVersion: SYNC_VERSION,
      createdAt: timestamp,
      lastModifiedAt: timestamp,
      savedAt: status === 'saved' ? timestamp : null,
      deviceId: DEVICE_ID,
      published,
    },
    config: { type: 'MIRROR_DUNGEON', category: '5F' },
    content: localPlannerContent(),
  }

  const key = [PLANNER_STORAGE_KEYS.PLANNER, plannerId].join(':')

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
        // Seed only what is absent: the script re-runs on every navigation, and an
        // unconditional put would clobber whatever the app wrote since the first load.
        const existing = store.get(script.key)
        existing.onsuccess = () => {
          if (existing.result === undefined) {
            store.put(script.deviceId, script.deviceIdKey)
            store.put(script.value, script.key)
          }
          // close() drains the queued puts first; a connection left open would block the
          // app's next higher-version open in onblocked, failing every storage read on the page.
          request.result.close()
        }
      }
    },
    {
      dbName: DB_NAME,
      dbVersion: DB_VERSION,
      store: STORAGE_STORE_NAME,
      deviceIdKey: PLANNER_STORAGE_KEYS.DEVICE_ID,
      deviceId: DEVICE_ID,
      key,
      value: JSON.stringify(planner),
    },
  )
}
