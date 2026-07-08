import { KEYWORD_RENAME_MAP } from './constants'

/**
 * Migrates renamed keyword aliases to their current ids, preserving everything else.
 *
 * Remaps {@link KEYWORD_RENAME_MAP} entries (e.g. AccelBullet → 9828) so a stale
 * client's planner renders and saves under the current id — users cannot hand-edit
 * stored keywords, so renames are handled by code. Unknown (non-renamed) ids are
 * deliberately NOT dropped: they are carried through so the strict publish tier can
 * reject them loudly (mirroring GIFT_UNKNOWN_ID / EQUIPMENT_INVALID_EGO_TYPES), rather
 * than being silently swallowed. Defensive against non-array input since it runs on
 * unvalidated (`z.unknown`) content. Dedupes to collapse an alias-and-current collision.
 *
 * @param raw - selectedKeywords as loaded/parsed (may be unknown/legacy)
 * @returns deduped array with legacy aliases remapped, unknown ids preserved
 */
export function migrateKeywords(raw: unknown): string[] {
  if (!Array.isArray(raw)) {
    return []
  }
  const seen = new Set<string>()
  for (const item of raw) {
    if (typeof item !== 'string') {
      continue
    }
    seen.add(KEYWORD_RENAME_MAP[item] ?? item)
  }
  return Array.from(seen)
}
