import { useIdentityDetailI18n } from '../hooks/useIdentityDetailData'
import { IdentityHeader } from './IdentityHeader'
import type { IdentityId } from '@/shared/gameData'

interface IdentityHeaderI18nProps {
  id: IdentityId
}

interface IdentityHeaderWithI18nProps {
  id: IdentityId
  rank: number
  uptie: number
}

export function IdentityHeaderWithI18n({ id, rank, uptie }: IdentityHeaderWithI18nProps) {
  const i18n = useIdentityDetailI18n(id)
  return <IdentityHeader identityId={id} name={i18n.name} rank={rank} uptie={uptie} />
}

export function IdentityHeaderI18n({ id }: IdentityHeaderI18nProps) {
  const i18n = useIdentityDetailI18n(id)

  // Render \n as line breaks for multi-line identity names
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
