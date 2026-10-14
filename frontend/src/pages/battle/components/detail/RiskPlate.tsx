import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'

import { RISK_PLATE_GRADE_COLORS, RISK_PLATE_UNITS } from '@/lib/constants'
import {
  getHazardGradedPlatePath,
  getHazardHiddenPlatePath,
  getHazardPlatePath,
  getHazardSupplementPlatePath,
  getLevelNumberHyphenPlatePath,
  getLevelNumberUndefPlatePath,
} from '@/shared/assets'
import type { RiskPlate as RiskPlateBadge } from '../../schemas/BattleDetailSchemas'

type GradeBox = (typeof RISK_PLATE_UNITS)['GRADE' | 'NONE_CLASS_GRADE']

interface DrawnPlate {
  src: string
  alt: string
  grade?: { text: string; color: string; box: GradeBox }
}

const UNDEF_PLATE_LANGUAGES: Record<string, 'kr' | 'en' | 'jp'> = {
  KR: 'kr',
  EN: 'en',
  JP: 'jp',
}

function drawnPlate(plate: RiskPlateBadge, language: string): DrawnPlate {
  switch (plate.kind) {
    case 'class':
      return plate.grade === undefined
        ? { src: getHazardPlatePath(plate.class), alt: plate.class }
        : {
            src: getHazardGradedPlatePath(plate.class),
            alt: plate.class,
            grade: {
              text: `-${plate.grade}`,
              color: RISK_PLATE_GRADE_COLORS[plate.class],
              box: RISK_PLATE_UNITS.GRADE,
            },
          }
    case 'supplement':
      return {
        src: getHazardSupplementPlatePath(),
        alt: 'SUPPLEMENT',
        grade: {
          text: `-${plate.grade}`,
          color: RISK_PLATE_GRADE_COLORS.SUPPLEMENT,
          box: RISK_PLATE_UNITS.NONE_CLASS_GRADE,
        },
      }
    case 'hidden':
      return { src: getHazardHiddenPlatePath(), alt: '???' }
    case 'levelNumber':
      return plate.which === 'UNDEF'
        ? {
            src: getLevelNumberUndefPlatePath(UNDEF_PLATE_LANGUAGES[language] ?? 'en'),
            alt: 'UNDEF.',
          }
        : { src: getLevelNumberHyphenPlatePath(), alt: '-' }
  }
}

function percent(units: number, whole: number): string {
  return `${String((units / whole) * 100)}%`
}

function gradeStyle(box: GradeBox, color: string): CSSProperties {
  const { WIDTH, HEIGHT, GRADE_FACE } = RISK_PLATE_UNITS
  const { UNDERLAY } = GRADE_FACE
  return {
    left: percent(WIDTH / 2 + box.CENTER_X - box.WIDTH / 2, WIDTH),
    top: percent(HEIGHT / 2 - box.CENTER_Y - box.HEIGHT / 2, HEIGHT),
    width: percent(box.WIDTH, WIDTH),
    height: percent(box.HEIGHT, HEIGHT),
    fontSize: `${String((box.FONT / WIDTH) * 100)}cqw`,
    fontFamily: GRADE_FACE.FAMILY,
    lineHeight: GRADE_FACE.LINE_HEIGHT,
    textShadow: `${String(UNDERLAY.X_EM)}em ${String(UNDERLAY.Y_EM)}em 0 ${UNDERLAY.COLOR}`,
    color,
  }
}

export function RiskPlate({ plate }: { plate: RiskPlateBadge }) {
  const { i18n } = useTranslation()
  const drawn = drawnPlate(plate, i18n.language)

  return (
    <div
      className="@container relative h-6"
      style={{
        aspectRatio: `${String(RISK_PLATE_UNITS.WIDTH)} / ${String(RISK_PLATE_UNITS.HEIGHT)}`,
      }}
    >
      <img
        data-testid="battle-hazard-plate"
        src={drawn.src}
        alt={drawn.alt}
        className="absolute inset-0 h-full w-full"
      />
      {drawn.grade !== undefined && (
        <span
          data-testid="battle-hazard-grade"
          className="absolute whitespace-nowrap"
          style={gradeStyle(drawn.grade.box, drawn.grade.color)}
        >
          {drawn.grade.text}
        </span>
      )}
    </div>
  )
}
