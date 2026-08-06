import { useTranslation } from 'react-i18next'

interface MdCategoryLabelProps {
  category: string
}

/** Localized name of a Mirror Dungeon category, as bare text. */
export function MdCategoryLabel({ category }: MdCategoryLabelProps) {
  const { t } = useTranslation('planner')

  return <>{t(`pages.plannerList.mdCategory.${category}`)}</>
}
