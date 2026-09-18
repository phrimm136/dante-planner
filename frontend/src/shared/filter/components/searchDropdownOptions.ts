export interface SearchDropdownOption {
  value: string
  label: string
}

export function buildNameOptions(
  ids: string[],
  names: Record<string, string>,
): SearchDropdownOption[] {
  return ids.map((id) => ({
    value: id,
    label: names[id] ?? id,
  }))
}

export function buildSinnerSuffixedOptions<Id extends string>(
  ids: Id[],
  names: Record<string, string>,
  getSinnerName: (id: Id) => string,
): SearchDropdownOption[] {
  return ids.map((id) => ({
    value: id,
    label: `${(names[id] ?? id).replace(/\n/g, ' ')} - ${getSinnerName(id)}`,
  }))
}
