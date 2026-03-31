import { useTranslation } from 'react-i18next'
import { useEGOGiftListI18n } from '@/hooks/useEGOGiftListData'

/**
 * Render cantSelectInThisCase condition as readable text.
 * e.g., HasNotEgoGift_9726 -> "E.G.O Gift 낙수의 잔 required"
 */
export function CantSelectCondition({ condition }: { condition: string }) {
  const { t } = useTranslation('database')
  const giftNames = useEGOGiftListI18n()

  if (condition.startsWith('HasNotEgoGift_')) {
    const giftId = condition.replace('HasNotEgoGift_', '')
    const giftName = giftNames[giftId] ?? giftId
    return <>{t('abEvent.requiresGift', { name: giftName, defaultValue: `Requires E.G.O Gift "${giftName}"` })}</>
  }

  if (condition.startsWith('HasNotEnoughCost_')) {
    const cost = condition.replace('HasNotEnoughCost_', '')
    return <>{t('abEvent.requiresCost', { cost, defaultValue: `Requires ${cost} Cost` })}</>
  }

  return <>{condition}</>
}
