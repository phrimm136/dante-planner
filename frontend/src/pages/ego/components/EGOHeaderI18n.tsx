import { useEGODetailI18n } from '../hooks/useEGODetailData'
import { EGOHeader } from './EGOHeader'
import type { EgoType } from '@/shared/gameData'
import type { EgoSkillType } from '../types/EGOTypes'
import type { EGOId } from '@/shared/gameData'

interface EGOHeaderI18nProps {
  id: EGOId
}

interface EGOHeaderWithI18nProps {
  id: EGOId
  rank: EgoType
  skillType: EgoSkillType
}

export function EGOHeaderWithI18n({ id, rank, skillType }: EGOHeaderWithI18nProps) {
  const i18n = useEGODetailI18n(id)
  return <EGOHeader egoId={id} name={i18n.name} rank={rank} skillType={skillType} />
}

export function EGOHeaderI18n({ id }: EGOHeaderI18nProps) {
  const i18n = useEGODetailI18n(id)

  // Render \n as line breaks for multi-line EGO names
  const lines = i18n.name.split('\n')
  if (lines.length === 1) {
    return <>{i18n.name}</>
  }

  return (
    <>
      {lines.map((line, index) => (
        <span key={`line-${index}`}>
          {line}
          {index < lines.length - 1 && <br />}
        </span>
      ))}
    </>
  )
}
