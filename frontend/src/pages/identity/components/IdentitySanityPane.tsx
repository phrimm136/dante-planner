import { PanicTypeSectionI18n, SanityConditionsSectionI18n } from './SanityI18n'

import type { IdentityData } from '../types/IdentityTypes'

interface IdentitySanityPaneProps {
  /** Panic type ID */
  panicType: number
  /** Sanity increase and decrease conditions */
  mentalConditionInfo: IdentityData['mentalConditionInfo']
}

/** Panic type and the sanity increase/decrease factors. */
export function IdentitySanityPane({ panicType, mentalConditionInfo }: IdentitySanityPaneProps) {
  return (
    <div className="border rounded p-4 space-y-4">
      {/* Panic Type - uses internal Suspense for name/description */}
      <PanicTypeSectionI18n panicType={panicType} />

      {/* Sanity Conditions - uses internal Suspense for condition text */}
      <SanityConditionsSectionI18n
        addConditions={mentalConditionInfo.add}
        minConditions={mentalConditionInfo.min}
      />
    </div>
  )
}
