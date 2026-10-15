import type { BattleKit, BattlePhase, EngagementRow } from '../schemas/BattleDetailSchemas'
import type { BattleForm } from './battleVocabulary'

export function rowFormKeys(row: EngagementRow | undefined): readonly BattleForm[] {
  return row?.forms ?? []
}

export function defaultFormKey(row: EngagementRow | undefined): BattleForm | undefined {
  return row?.forms?.[0]
}

export function formsLocked(row: EngagementRow | undefined): boolean {
  return rowFormKeys(row).length <= 1
}

export function selectedFormKey(
  row: EngagementRow | undefined,
  formKey: BattleForm | undefined,
): BattleForm | undefined {
  const allowed = rowFormKeys(row)
  return formKey !== undefined && allowed.includes(formKey) ? formKey : defaultFormKey(row)
}

export function activePhase(
  kit: BattleKit,
  row: EngagementRow | undefined,
  phaseIndex: number,
  formKey: BattleForm | undefined,
): BattlePhase {
  const key = selectedFormKey(row, formKey)
  const form = key === undefined ? undefined : kit.forms?.find((each) => each.key === key)
  return form?.phase ?? kit.phases[phaseIndex] ?? kit.phases[0]
}
