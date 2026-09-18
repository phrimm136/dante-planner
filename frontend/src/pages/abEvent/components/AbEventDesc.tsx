import { useTranslation } from 'react-i18next'
import { useAbEventListI18n } from '../hooks/useAbEventListData'
import { KoreanText } from '@/components/ui/KoreanText'

interface AbEventDescProps {
  id: string
}

export const AbEventDesc = function AbEventDesc({ id }: AbEventDescProps) {
  const { i18n } = useTranslation()
  const descs = useAbEventListI18n()
  const desc = descs[id] || ''

  if (i18n.language === 'KR') {
    return <KoreanText>{desc}</KoreanText>
  }

  return <>{desc}</>
}
