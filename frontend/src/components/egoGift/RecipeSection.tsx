/**
 * RecipeSection - Displays EGO Gift fusion recipes
 *
 * Shows ingredient cards joined with "+" separator using game-style numeric font.
 * Handles two recipe types:
 * - Standard: Fixed ingredient combinations (materials array)
 * - Mixed (Lunar Memory): Pick N from pool A + M from pool B
 *
 * Multiple recipes are displayed vertically.
 */

import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftName } from './EGOGiftName'
import { Skeleton } from '@/components/ui/skeleton'
import { useEGOGiftListSpec } from '@/hooks/useEGOGiftListData'
import { getDisplayFontForNumeric } from '@/lib/utils'
import type { EGOGiftRecipe, EGOGiftListItem, StandardRecipe, MixedRecipe } from '@/types/EGOGiftTypes'

interface RecipeSectionProps {
  recipe: EGOGiftRecipe
}

/**
 * Type guard to check if recipe is mixed type (Lunar Memory)
 */
function isMixedRecipe(recipe: EGOGiftRecipe): recipe is MixedRecipe {
  return 'type' in recipe && recipe.type === 'mixed'
}

/**
 * Single ingredient card with link and name
 */
function IngredientCard({ gift }: { gift: EGOGiftListItem }) {
  return (
    <Link
      to="/ego-gift/$id"
      params={{ id: gift.id }}
      className="block transition-all hover:scale-105"
    >
      <div className="flex flex-col items-center gap-1">
        <EGOGiftCard gift={gift} enhancement={0} />
        <span className="text-xs text-center text-foreground line-clamp-2 w-24 leading-tight font-medium">
          <Suspense fallback={<Skeleton className="h-5 w-20 bg-foreground" />}>
            <EGOGiftName id={gift.id} />
          </Suspense>
        </span>
      </div>
    </Link>
  )
}

/**
 * Plus separator using game-style numeric font
 */
function PlusSeparator() {
  return (
    <span
      className="text-3xl font-bold text-muted-foreground self-center text-center mx-1 -translate-y-4"
      style={{ fontFamily: getDisplayFontForNumeric() }}
    >
      +
    </span>
  )
}

/**
 * Displays a single standard recipe row (ingredient + ingredient + ...)
 */
function StandardRecipeRow({
  ingredients,
  specMap,
}: {
  ingredients: number[]
  specMap: Record<string, import('@/types/EGOGiftTypes').EGOGiftSpec>
}) {
  return (
    <div className="flex flex-wrap items-start">
      {ingredients.map((id, index) => {
        const spec = specMap[String(id)]
        if (!spec) return null

        const gift: EGOGiftListItem = {
          id: String(id),
          tag: spec.tag,
          keyword: spec.keyword,
          attributeType: spec.attributeType,
          themePack: spec.themePack,
          hardOnly: spec.hardOnly,
          extremeOnly: spec.extremeOnly,
        }

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

/**
 * Displays mixed recipe (Lunar Memory special case)
 * Shows: "Select N of M" for pool A + all cards from pool B (when count === total)
 */
function MixedRecipeDisplay({
  recipe,
  specMap,
}: {
  recipe: MixedRecipe
  specMap: Record<string, import('@/types/EGOGiftTypes').EGOGiftSpec>
}) {
  const { t } = useTranslation()

  // Only show label when count !== total (selection required)
  const showPoolALabel = recipe.a.count !== recipe.a.ids.length
  const showPoolBLabel = recipe.b.count !== recipe.b.ids.length

  return (
    <div className="space-y-4">
      {/* Pool A */}
      <div className="space-y-2">
        {showPoolALabel && (
          <p className="text-sm text-muted-foreground">
            {t('recipe.selectNofM', { count: recipe.a.count, total: recipe.a.ids.length })}
          </p>
        )}
        <div className="flex flex-wrap items-start gap-2">
          {recipe.a.ids.map((id) => {
            const spec = specMap[String(id)]
            if (!spec) return null

            const gift: EGOGiftListItem = {
              id: String(id),
              tag: spec.tag,
              keyword: spec.keyword,
              attributeType: spec.attributeType,
              themePack: spec.themePack,
            }

            return <IngredientCard key={id} gift={gift} />
          })}
        </div>
      </div>

      {/* Plus between pools */}
      <div className="flex translate-x-9 translate-y-2">
        <PlusSeparator />
      </div>

      {/* Pool B */}
      <div className="space-y-2">
        {showPoolBLabel && (
          <p className="text-sm text-muted-foreground">
            {t('recipe.selectNofM', { count: recipe.b.count, total: recipe.b.ids.length })}
          </p>
        )}
        <div className="flex flex-wrap items-start gap-2">
          {recipe.b.ids.map((id) => {
            const spec = specMap[String(id)]
            if (!spec) return null

            const gift: EGOGiftListItem = {
              id: String(id),
              tag: spec.tag,
              keyword: spec.keyword,
              attributeType: spec.attributeType,
              themePack: spec.themePack,
            }

            return <IngredientCard key={id} gift={gift} />
          })}
        </div>
      </div>
    </div>
  )
}

/**
 * Recipe content - fetches spec data for ingredient lookup
 */
function RecipeSectionContent({ recipe }: RecipeSectionProps) {
  const specMap = useEGOGiftListSpec()

  if (isMixedRecipe(recipe)) {
    return (
      <div className="border rounded-lg p-4">
        <MixedRecipeDisplay recipe={recipe} specMap={specMap} />
      </div>
    )
  }

  // Standard recipe - may have multiple alternatives
  const standardRecipe = recipe as StandardRecipe

  return (
    <div className="border rounded-lg p-4 space-y-4">
      {standardRecipe.materials.map((ingredients, index) => (
        <StandardRecipeRow key={index} ingredients={ingredients} specMap={specMap} />
      ))}
    </div>
  )
}

/**
 * RecipeSection - Main export with Suspense boundary
 */
export function RecipeSection({ recipe }: RecipeSectionProps) {
  return (
    <Suspense fallback={<Skeleton className="h-40 w-full" />}>
      <RecipeSectionContent recipe={recipe} />
    </Suspense>
  )
}
