import type { CSSProperties } from 'react'

import { NAME_PLATE_UNITS } from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import { getNamePlatePath } from '@/shared/assets'

export type NamePlateKind = 'skill' | 'passive'

const PLATE_SLICES = {
  skill: NAME_PLATE_UNITS.SKILL.SLICE,
  passive: NAME_PLATE_UNITS.PASSIVE.SLICE,
}

function percent(ratio: number): string {
  return `${String(Number((ratio * 100).toFixed(4)))}%`
}

function slicePiece(
  image: string,
  size: string,
  position: string,
  tint: string | undefined,
): CSSProperties {
  return {
    height: '100%',
    backgroundImage: image,
    backgroundSize: size,
    backgroundPosition: position,
    backgroundRepeat: 'no-repeat',
    maskImage: image,
    maskSize: size,
    maskPosition: position,
    maskRepeat: 'no-repeat',
    ...(tint !== undefined && { backgroundColor: tint, backgroundBlendMode: 'multiply' }),
  }
}

export function namePlateSliceStyles(
  kind: NamePlateKind,
  tint: string | undefined,
): Record<'left' | 'centre' | 'right', CSSProperties> {
  const { LEFT, RIGHT, SPRITE_WIDTH } = PLATE_SLICES[kind]
  const image = `url(${getNamePlatePath(kind)})`
  const fullSize = `${clientLength(SPRITE_WIDTH)} 100%`
  const centreSize = `${percent(SPRITE_WIDTH / (SPRITE_WIDTH - LEFT - RIGHT))} 100%`
  return {
    left: {
      width: clientLength(LEFT),
      flexShrink: 0,
      ...slicePiece(image, fullSize, 'left top', tint),
    },
    centre: {
      flexGrow: 1,
      ...slicePiece(image, centreSize, `${percent(LEFT / (LEFT + RIGHT))} top`, tint),
    },
    right: {
      width: clientLength(RIGHT),
      flexShrink: 0,
      ...slicePiece(image, fullSize, 'right top', tint),
    },
  }
}
