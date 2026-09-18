import { useTranslation } from 'react-i18next'
import { useEGOGiftListI18n } from '../hooks/useEGOGiftListData'
import { KoreanText } from '@/components/ui/KoreanText'

interface EGOGiftNameProps {
  id: string
}

export const EGOGiftName = function EGOGiftName({ id }: EGOGiftNameProps) {
  const { i18n } = useTranslation()
  const names = useEGOGiftListI18n()
  const name = names[id] || ''

  if (i18n.language === 'KR') {
    return <KoreanText>{name}</KoreanText>
  }

  return <>{name}</>
}
