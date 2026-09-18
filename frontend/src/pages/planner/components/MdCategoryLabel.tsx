import { useTranslation } from 'react-i18next'

interface MdCategoryLabelProps {
  category: string
}

export function MdCategoryLabel({ category }: MdCategoryLabelProps) {
  const { t } = useTranslation('planner')

  return <>{t(`pages.plannerList.mdCategory.${category}`)}</>
}
