import type { CSSProperties } from 'react'

import { NAME_PLATE_UNITS } from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import { getNamePlatePath } from '@/shared/assets'

export type NamePlateKind = 'skill' | 'passive'

const PLATE_SLICES = {
  skill: NAME_PLATE_UNITS.SKILL.SLICE,
  passive: NAME_PLATE_UNITS.PASSIVE.SLICE,
}

const CHANNEL_MAX = 255

function percent(ratio: number): string {
  return `${String(Number((ratio * 100).toFixed(4)))}%`
}

function slicePiece(
  image: string,
  size: string,
  position: string,
  filterId: string | undefined,
): CSSProperties {
  return {
    height: '100%',
    backgroundImage: image,
    backgroundSize: size,
    backgroundPosition: position,
    backgroundRepeat: 'no-repeat',
    ...(filterId !== undefined && { filter: `url(#${filterId})` }),
  }
}

export function tintColorMatrix(tint: string): string {
  const [red, green, blue] = [1, 3, 5].map((start) =>
    String(Number((Number.parseInt(tint.slice(start, start + 2), 16) / CHANNEL_MAX).toFixed(4))),
  )
  return `${red} 0 0 0 0  0 ${green} 0 0 0  0 0 ${blue} 0 0  0 0 0 1 0`
}

export function namePlateSliceStyles(
  kind: NamePlateKind,
  filterId: string | undefined,
): Record<'left' | 'centre' | 'right', CSSProperties> {
  const { LEFT, RIGHT, SPRITE_WIDTH, IMAGE_WIDTH, SPRITE_TEXELS_HEIGHT, IMAGE_TEXELS_HEIGHT } =
    PLATE_SLICES[kind]
  const image = `url(${getNamePlatePath(kind)})`
  const imageHeight = percent(IMAGE_TEXELS_HEIGHT / SPRITE_TEXELS_HEIGHT)
  const capSize = `${clientLength(IMAGE_WIDTH)} ${imageHeight}`
  const centreWidth = SPRITE_WIDTH - LEFT - RIGHT
  const centreSize = `${percent(IMAGE_WIDTH / centreWidth)} ${imageHeight}`
  return {
    left: {
      width: clientLength(LEFT),
      flexShrink: 0,
      ...slicePiece(image, capSize, 'left bottom', filterId),
    },
    centre: {
      flexGrow: 1,
      ...slicePiece(
        image,
        centreSize,
        `${percent(LEFT / (IMAGE_WIDTH - centreWidth))} bottom`,
        filterId,
      ),
    },
    right: {
      width: clientLength(RIGHT),
      flexShrink: 0,
      ...slicePiece(image, capSize, `${clientLength(RIGHT - SPRITE_WIDTH)} bottom`, filterId),
    },
  }
}
