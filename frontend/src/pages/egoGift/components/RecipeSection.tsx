import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { CardSlot, EGO_GIFT_GEOMETRY } from '@/shared/cardLayout'
import { CARD_MOBILE_SCALE_NONE } from '@/lib/constants'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftName } from './EGOGiftName'
import { Skeleton } from '@/components/ui/skeleton'
import { useEGOGiftListSpec } from '../hooks/useEGOGiftListData'
import type {
  EGOGiftRecipe,
  EGOGiftEntity,
  StandardRecipe,
  MixedRecipe,
} from '../types/EGOGiftTypes'
import { isMixedRecipe } from '../lib/egoGiftUtils'
import { SECTION_STYLES } from '@/lib/constants'
import type { EGOGiftId } from '@/shared/gameData'
import { toEGOGiftEntity } from '../lib/egoGiftEntity'

interface RecipeSectionProps {
  recipe: EGOGiftRecipe
}

function IngredientCard({ gift }: { gift: EGOGiftEntity }) {
  return (
    <Link to="/ego-gift/$id" params={{ id: gift.id }} className="block">
      <div className="flex flex-col items-center gap-1">
        <CardSlot size={EGO_GIFT_GEOMETRY.size} mobileScale={CARD_MOBILE_SCALE_NONE}>
          <EGOGiftCard gift={gift} enhancement={0} enableHoverHighlight />
        </CardSlot>
        <span className="text-xs text-center text-foreground line-clamp-2 w-24 leading-tight font-medium">
          <Suspense fallback={<Skeleton className="h-5 w-20" />}>
            <EGOGiftName id={gift.id} />
          </Suspense>
        </span>
      </div>
    </Link>
  )
}

function PlusSeparator() {
  return (
    <span className="text-3xl font-bold text-muted-foreground self-center text-center mx-1 -translate-y-2.5">
      +
    </span>
  )
}

function StandardRecipeRow({
  ingredients,
  specMap,
}: {
  ingredients: EGOGiftId[]
  specMap: Record<string, import('../types/EGOGiftTypes').EGOGiftSpec>
}) {
  return (
    <div className="flex flex-wrap items-start">
      {ingredients.map((id, index) => {
        const spec = specMap[String(id)]
        if (!spec) return null

        const gift: EGOGiftEntity = toEGOGiftEntity(String(id), spec)

        return (
          <div key={id} className="flex items-start">
            {index > 0 && <PlusSeparator />}
            <IngredientCard gift={gift} />
          </div>
        )
      })}
    </div>
  )
}

function MixedRecipeDisplay({
  recipe,
  specMap,
}: {
  recipe: MixedRecipe
  specMap: Record<string, import('../types/EGOGiftTypes').EGOGiftSpec>
}) {
  const { t } = useTranslation()

  const showPoolALabel = recipe.a.count !== recipe.a.ids.length
  const showPoolBLabel = recipe.b.count !== recipe.b.ids.length

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        {showPoolALabel && (
          <p className={SECTION_STYLES.TEXT.caption}>
            {t('recipe.selectNofM', { count: recipe.a.count, total: recipe.a.ids.length })}
          </p>
        )}
        <div className="flex flex-wrap items-start gap-2">
          {recipe.a.ids.map((id) => {
            const spec = specMap[String(id)]
            if (!spec) return null

            const gift: EGOGiftEntity = toEGOGiftEntity(String(id), spec)

            return <IngredientCard key={id} gift={gift} />
          })}
        </div>
      </div>

      <div className="flex translate-x-9 translate-y-2">
        <PlusSeparator />
      </div>

      <div className="space-y-2">
        {showPoolBLabel && (
          <p className={SECTION_STYLES.TEXT.caption}>
            {t('recipe.selectNofM', { count: recipe.b.count, total: recipe.b.ids.length })}
          </p>
        )}
        <div className="flex flex-wrap items-start gap-2">
          {recipe.b.ids.map((id) => {
            const spec = specMap[String(id)]
            if (!spec) return null

            const gift: EGOGiftEntity = toEGOGiftEntity(String(id), spec)

            return <IngredientCard key={id} gift={gift} />
          })}
        </div>
      </div>
    </div>
  )
}

function RecipeSectionContent({ recipe }: RecipeSectionProps) {
  const specMap = useEGOGiftListSpec()

  if (isMixedRecipe(recipe)) {
    return (
      <div className="border rounded-lg p-4">
        <MixedRecipeDisplay recipe={recipe} specMap={specMap} />
      </div>
    )
  }

  const standardRecipe = recipe as StandardRecipe

  return (
    <div className="border rounded-lg p-4 space-y-4">
      {standardRecipe.materials.map((ingredients, index) => (
        <StandardRecipeRow key={index} ingredients={ingredients} specMap={specMap} />
      ))}
    </div>
  )
}

export function RecipeSection({ recipe }: RecipeSectionProps) {
  return (
    <Suspense fallback={<Skeleton className="h-40 w-full" />}>
      <RecipeSectionContent recipe={recipe} />
    </Suspense>
  )
}
