import { SINNERS } from '@/shared/gameData'

const SINNER_KEYS = SINNERS.map((_, sinnerIndex) => String(sinnerIndex + 1))

const sinnerIdPart = (sinnerKey: string) => sinnerKey.padStart(2, '0')

export const publishedContent = {
  selectedKeywords: ['Combustion'],
  selectedBuffIds: [101],
  selectedGiftKeyword: 'Combustion',
  selectedGiftIds: ['9001'],
  observationGiftIds: ['9002'],
  comprehensiveGiftIds: ['19003'],
  equipment: Object.fromEntries(
    SINNER_KEYS.map((sinnerKey) => [
      sinnerKey,
      {
        identity: { id: `1${sinnerIdPart(sinnerKey)}01`, level: 45, uptie: 4 },
        egos: { ZAYIN: { id: `2${sinnerIdPart(sinnerKey)}01`, threadspin: 4 } },
      },
    ]),
  ),
  deploymentOrder: [0, 1, 2],
  skillEAState: Object.fromEntries(
    SINNER_KEYS.map((sinnerKey) => [sinnerKey, { 0: 3, 1: 2, 2: 1 }]),
  ),
  floorSelections: [{ themePackId: '1001', difficulty: 1, giftIds: ['9001'] }],
  sectionNotes: {},
}

export const publishedPayload = {
  id: '00000000-0000-4000-8000-000000000001',
  title: 'Published run',
  plannerType: 'MIRROR_DUNGEON',
  category: '5F',
  selectedKeywords: ['Combustion'],
  upvotes: 0,
  viewCount: 0,
  createdAt: '2026-01-01T00:00:00Z',
  firstPublishedAt: '2026-01-01T00:00:00Z',
  hasUpvoted: false,
  commentCount: 0,
  lastModifiedAt: '2026-01-02T00:00:00Z',
  content: JSON.stringify(publishedContent),
  schemaVersion: 2,
  contentVersion: 7,
  status: 'saved',
  syncVersion: 1,
  isSubscribed: false,
  hasReported: false,
  ownerNotificationsEnabled: false,
}
