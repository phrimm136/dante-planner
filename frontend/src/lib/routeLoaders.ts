import i18n from '@/lib/i18n'
import { queryClient } from '@/lib/queryClient'
import { loadPlannerTitle, untitledPlannerTitle } from '@/pages/planner/lib/loadPlannerTitle'

/**
 * Route loaders. Each resolves the one localized string its route's head()
 * publishes as the document title.
 *
 * The `@static` specifiers stay written as literal templates because the
 * bundler needs the shape to enumerate the matching files.
 */

/** Read `name` out of a per-entity i18n file, falling back to the raw id. */
async function entityName(dir: string, id: string, stripNewlines: boolean): Promise<string> {
  const module = await import(`@static/i18n/${i18n.language}/${dir}/${id}.json`)
  const name = (module.default as { name?: string }).name
  if (name === undefined) return id
  return stripNewlines ? name.replace(/\n/g, ' ') : name
}

/** Read `name` for one id out of a single keyed i18n file. */
async function nameFromIndex(file: string, id: string): Promise<string> {
  const module = await import(`@static/i18n/${i18n.language}/${file}.json`)
  return (module.default as Record<string, { name?: string }>)[id]?.name ?? id
}

export async function loadPublishedPlanner({ params }: { params: { id: string } }) {
  // Dynamic so the published-planner schemas stay out of the entry chunk.
  const {
    publishedPlannerQueryKeys,
    fetchPublishedPlanner,
    isPlannerRemoved,
    publishedPlannerStaleTime,
  } = await import('@/pages/planner/hooks/usePublishedPlannerQuery')
  const result = await queryClient.fetchQuery({
    queryKey: publishedPlannerQueryKeys.detail(params.id),
    queryFn: ({ signal }) => fetchPublishedPlanner(params.id, signal),
    staleTime: (query) => publishedPlannerStaleTime(query.state.data),
  })
  if (isPlannerRemoved(result)) return { title: untitledPlannerTitle() }
  return { title: result.apiData.title || untitledPlannerTitle() }
}

export async function loadPlannerTitleRoute({ params }: { params: { id: string } }) {
  const title = await loadPlannerTitle(params.id)
  return { title }
}

export async function loadIdentityName({ params }: { params: { id: string } }) {
  return { name: await entityName('identity', params.id, true) }
}

export async function loadEgoName({ params }: { params: { id: string } }) {
  return { name: await entityName('ego', params.id, true) }
}

export async function loadEgoGiftName({ params }: { params: { id: string } }) {
  return { name: await entityName('egoGift', params.id, false) }
}

export async function loadThemePackName({ params }: { params: { id: string } }) {
  return { name: await nameFromIndex('themePack', params.id) }
}

export async function loadKeywordName({ params }: { params: { id: string } }) {
  return { name: await nameFromIndex('battleKeywords', params.id) }
}

export async function loadAbEventTitle({ params }: { params: { id: string } }) {
  try {
    const module = await import(`@static/i18n/${i18n.language}/abEvent/${params.id}.json`)
    const data = module.default as { desc?: string }
    const raw = (data.desc ?? '').replace(/\n/g, ' ')
    const snippet = raw.length > 20 ? `${raw.slice(0, 20)}...` : raw
    return { title: snippet || params.id }
  } catch {
    return { title: params.id }
  }
}
