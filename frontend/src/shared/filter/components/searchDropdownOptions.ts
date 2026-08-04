/**
 * Search Dropdown Option Builders
 *
 * Shared option-building for the SearchableMultiSelect dropdown family.
 * Missing localized names fall back to the raw id in both builders.
 */

export interface SearchDropdownOption {
  value: string
  label: string
}

/**
 * Builds options from an id → localized-name map.
 *
 * Used by dropdowns whose entities are not sinner-owned (e.g. EGO gifts) —
 * no sinner suffix and no newline flattening, matching their raw names.
 */
export function buildNameOptions(
  ids: string[],
  names: Record<string, string>,
): SearchDropdownOption[] {
  return ids.map((id) => ({
    value: id,
    label: names[id] ?? id,
  }))
}

/**
 * Builds options labeled "<entity name> - <sinner name>" for sinner-owned
 * entities (identities, EGOs), flattening newlines in the entity name.
 *
 * @param getSinnerName - Resolves an entity id to its localized sinner name
 */
export function buildSinnerSuffixedOptions(
  ids: string[],
  names: Record<string, string>,
  getSinnerName: (id: string) => string,
): SearchDropdownOption[] {
  return ids.map((id) => ({
    value: id,
    label: `${(names[id] ?? id).replace(/\n/g, ' ')} - ${getSinnerName(id)}`,
  }))
}
