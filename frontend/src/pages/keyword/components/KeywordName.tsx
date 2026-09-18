import { useTranslation } from 'react-i18next'
import { useKeywordListI18n } from '@/shared/gameText'
import { KoreanText } from '@/components/ui/KoreanText'

interface KeywordNameProps {
  id: string
}

export const KeywordName = function KeywordName({ id }: KeywordNameProps) {
  const { i18n } = useTranslation()
  const names = useKeywordListI18n()
  const name = names[id]?.name ?? ''

  if (i18n.language === 'KR') {
    return <KoreanText>{name}</KoreanText>
  }

  return <>{name}</>
}
