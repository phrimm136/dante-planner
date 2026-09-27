import i18n from '@/lib/i18n'
import { queryClient } from '@/lib/queryClient'
import { fetchPublishedPlannerRaw } from '@/pages/planner/lib/fetchPublishedPlannerRaw'
import { loadPlannerTitle, untitledPlannerTitle } from '@/pages/planner/lib/loadPlannerTitle'
import { publishedPlannerQueryKeys } from '@/pages/planner/lib/publishedPlannerQueryKeys'
import type { EntityDetailDataConfig, EntityListDataConfig } from '@/shared/entityCatalog'

/**
 * Route loaders. Each resolves the localized string its route's head()
 * publishes as the document title, if any, and caches the language-neutral
 * game data its page renders with.
 *
 * The `@static` specifiers stay written as literal templates because the
 * bundler needs the shape to enumerate the matching files.
 */

async function ensureSpecs(specs: Promise<unknown>[]): Promise<void> {
  await Promise.allSettled(specs)
}

async function ensureListSpec<TSpec, TI18n>(
  config: Promise<EntityListDataConfig<TSpec, TI18n>>,
): Promise<TSpec> {
  const [{ entityListSpecOptions }, cfg] = await Promise.all([
    import('@/shared/entityCatalog/hooks/useEntityListData'),
    config,
  ])
  return queryClient.ensureQueryData(entityListSpecOptions(cfg))
}

async function ensureDetailSpec<TSpec, TI18n>(
  config: Promise<EntityDetailDataConfig<TSpec, TI18n>>,
  id: string,
): Promise<TSpec> {
  const [{ entityDetailSpecOptions }, cfg] = await Promise.all([
    import('@/shared/entityCatalog/hooks/useEntityDetailData'),
    config,
  ])
  return queryClient.ensureQueryData(entityDetailSpecOptions(cfg, id))
}

function identityListSpec() {
  return ensureListSpec(
    import('@/pages/identity/hooks/useIdentityListData').then((m) => m.IDENTITY_LIST),
  )
}

function egoListSpec() {
  return ensureListSpec(import('@/pages/ego/hooks/useEGOListData').then((m) => m.EGO_LIST))
}

function egoGiftListSpec() {
  return ensureListSpec(
    import('@/pages/egoGift/hooks/useEGOGiftListData').then((m) => m.EGO_GIFT_LIST),
  )
}

function themePackListSpec() {
  return ensureListSpec(
    import('@/pages/themePack/hooks/useThemePackListData').then((m) => m.THEME_PACK_LIST),
  )
}

function abEventListSpec() {
  return ensureListSpec(
    import('@/pages/abEvent/hooks/useAbEventListData').then((m) => m.AB_EVENT_LIST),
  )
}

function keywordListSpec() {
  return ensureListSpec(
    import('@/shared/gameText/hooks/useKeywordListData').then((m) => m.KEYWORD_LIST),
  )
}

async function colorCodeSpec() {
  const { createColorCodeQueryOptions } = await import('@/shared/gameText/hooks/useColorCodes')
  return queryClient.ensureQueryData(createColorCodeQueryOptions())
}

function plannerSpecs() {
  return [identityListSpec(), egoListSpec(), egoGiftListSpec(), themePackListSpec()]
}

export async function loadPublishedPlanner({
  params,
  abortController,
}: {
  params: { id: string }
  abortController: AbortController
}) {
  const queryKey = publishedPlannerQueryKeys.detail(params.id)
  const cached = queryClient.getQueryState(queryKey)
  let early =
    cached?.data === undefined && cached?.fetchStatus !== 'fetching'
      ? fetchPublishedPlannerRaw(params.id, abortController.signal)
      : undefined
  void early?.catch(() => undefined)
  const { parsePublishedPlanner, isPlannerRemoved, publishedPlannerStaleTime } =
    await import('@/pages/planner/hooks/usePublishedPlannerQuery')
  const result = await queryClient.fetchQuery({
    queryKey,
    queryFn: ({ signal }) => {
      const raw = early ?? fetchPublishedPlannerRaw(params.id, signal)
      early = undefined
      return parsePublishedPlanner(params.id, raw)
    },
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
  const module = await import(`@static/i18n/${i18n.language}/identity/${params.id}.json`)
  const name = (module.default as { name?: string }).name?.replace(/\n/g, ' ') ?? params.id
  return { name }
}

export async function loadEgoName({ params }: { params: { id: string } }) {
  const module = await import(`@static/i18n/${i18n.language}/ego/${params.id}.json`)
  const name = (module.default as { name?: string }).name?.replace(/\n/g, ' ') ?? params.id
  return { name }
}

export async function loadEgoGiftName({ params }: { params: { id: string } }) {
  const module = await import(`@static/i18n/${i18n.language}/egoGift/${params.id}.json`)
  const name = (module.default as { name?: string }).name ?? params.id
  return { name }
}

export async function loadThemePackName({ params }: { params: { id: string } }) {
  const module = await import(`@static/i18n/${i18n.language}/themePack.json`)
  const name = (module.default as Record<string, { name?: string }>)[params.id]?.name ?? params.id
  return { name }
}

export async function loadKeywordName({ params }: { params: { id: string } }) {
  const module = await import(`@static/i18n/${i18n.language}/battleKeywords.json`)
  const keywords = module.default as Record<string, { name?: string }>
  const name = keywords[params.id]?.name ?? params.id
  return { name }
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

export async function loadPublishedPlannerRoute(context: {
  params: { id: string }
  abortController: AbortController
}) {
  const [loaded] = await Promise.all([loadPublishedPlanner(context), ensureSpecs(plannerSpecs())])
  return loaded
}

export async function loadSavedPlannerRoute(context: { params: { id: string } }) {
  const [loaded] = await Promise.all([loadPlannerTitleRoute(context), ensureSpecs(plannerSpecs())])
  return loaded
}

export async function loadDeckBuilder() {
  await ensureSpecs([identityListSpec(), egoListSpec()])
}

export async function loadIdentityList() {
  await ensureSpecs([identityListSpec()])
}

export async function loadIdentityDetail(context: { params: { id: string } }) {
  const identityDetail = import('@/pages/identity/hooks/useIdentityDetailData').then(
    (m) => m.IDENTITY_DETAIL,
  )
  const [loaded] = await Promise.all([
    loadIdentityName(context),
    ensureSpecs([
      ensureDetailSpec(identityDetail, context.params.id),
      keywordListSpec(),
      colorCodeSpec(),
    ]),
  ])
  return loaded
}

export async function loadEgoList() {
  await ensureSpecs([egoListSpec()])
}

export async function loadEgoDetail(context: { params: { id: string } }) {
  const egoDetail = import('@/pages/ego/hooks/useEGODetailData').then((m) => m.EGO_DETAIL)
  const [loaded] = await Promise.all([
    loadEgoName(context),
    ensureSpecs([
      ensureDetailSpec(egoDetail, context.params.id),
      keywordListSpec(),
      colorCodeSpec(),
    ]),
  ])
  return loaded
}

export async function loadEgoGiftList() {
  await ensureSpecs([egoGiftListSpec()])
}

export async function loadEgoGiftDetail(context: { params: { id: string } }) {
  const egoGiftDetail = import('@/pages/egoGift/hooks/useEGOGiftDetailData').then(
    (m) => m.EGO_GIFT_DETAIL,
  )
  const [loaded] = await Promise.all([
    loadEgoGiftName(context),
    ensureSpecs([
      ensureDetailSpec(egoGiftDetail, context.params.id),
      keywordListSpec(),
      colorCodeSpec(),
    ]),
  ])
  return loaded
}

export async function loadThemePackList() {
  await ensureSpecs([themePackListSpec()])
}

export async function loadThemePackDetail(context: { params: { id: string } }) {
  const themePackDetail = import('@/pages/themePack/hooks/useThemePackDetailData').then((m) =>
    queryClient.ensureQueryData(m.createThemePackDetailQueryOptions(context.params.id)),
  )
  const [loaded] = await Promise.all([
    loadThemePackName(context),
    ensureSpecs([themePackDetail, themePackListSpec(), egoGiftListSpec(), abEventListSpec()]),
  ])
  return loaded
}

export async function loadAbEventList() {
  await ensureSpecs([abEventListSpec()])
}

export async function loadAbEventDetail(context: { params: { id: string } }) {
  const abEventDetail = import('@/pages/abEvent/hooks/useAbEventDetailData').then(
    (m) => m.AB_EVENT_DETAIL,
  )
  const [loaded] = await Promise.all([
    loadAbEventTitle(context),
    ensureSpecs([
      ensureDetailSpec(abEventDetail, context.params.id),
      abEventListSpec(),
      egoGiftListSpec(),
    ]),
  ])
  return loaded
}

export async function loadKeywordList() {
  await ensureSpecs([keywordListSpec()])
}

export async function loadKeywordDetail(context: { params: { id: string } }) {
  const [loaded] = await Promise.all([
    loadKeywordName(context),
    ensureSpecs([keywordListSpec(), colorCodeSpec()]),
  ])
  return loaded
}
