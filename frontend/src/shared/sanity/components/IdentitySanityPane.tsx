import { PanicTypeSectionI18n, SanityConditionsSectionI18n } from './SanityI18n'

interface MentalConditionInfo {
  add: string[]
  min: string[]
}

interface IdentitySanityPaneProps {
  panicType: string
  mentalConditionInfo: MentalConditionInfo
}

export function IdentitySanityPane({ panicType, mentalConditionInfo }: IdentitySanityPaneProps) {
  return (
    <div className="border rounded p-4 space-y-4">
      <PanicTypeSectionI18n panicType={panicType} />

      <SanityConditionsSectionI18n
        addConditions={mentalConditionInfo.add}
        minConditions={mentalConditionInfo.min}
      />
    </div>
  )
}
