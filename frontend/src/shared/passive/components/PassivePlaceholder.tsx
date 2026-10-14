import type { CSSProperties, ReactNode } from 'react'

import { PASSIVE_BOX_UNITS, SECTION_STYLES } from '@/lib/constants'
import { clientLength } from '@/lib/utils'

const INSET_STYLE: CSSProperties = {
  paddingLeft: clientLength(PASSIVE_BOX_UNITS.INFO.LEFT),
  paddingRight: clientLength(PASSIVE_BOX_UNITS.INFO.RIGHT),
}

export function PassivePlaceholder({ children }: { children: ReactNode }) {
  return (
    <div className={SECTION_STYLES.TEXT.caption} style={INSET_STYLE}>
      {children}
    </div>
  )
}
