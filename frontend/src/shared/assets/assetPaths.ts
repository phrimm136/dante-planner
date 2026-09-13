import { AFFINITIES, ATK_TYPES } from '@/shared/gameData'
import type { SkillAttributeType } from '@/shared/gameData'
import { resolveAsset } from './assetManifest'

const AFFINITY_TO_SIN_NAME: Record<string, string> = {
  CRIMSON: 'Wrath',
  SCARLET: 'Lust',
  AMBER: 'Sloth',
  SHAMROCK: 'Gluttony',
  AZURE: 'Gloom',
  INDIGO: 'Pride',
  VIOLET: 'Envy',
}

const frameLevel = (skillTier: number): number => Math.max(1, Math.min(3, skillTier))

const pascalWord = (word: string): string =>
  word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()

const camelEnum = (enumValue: string): string =>
  enumValue
    .split('_')
    .map((word, index) => (index === 0 ? word.toLowerCase() : pascalWord(word)))
    .join('')

/**
 * The `hashStaticPlugin` build transform rewrites this file only, by regex: every `resolveAsset`
 * call taking a string or template literal becomes the content-hashed path (or a hash
 * continuation chain when interpolated), and the `resolveAsset` import is dropped. So an entry
 * must pass a literal, and each interpolation must hold a bare identifier or call — the transform
 * splices the expression into `"" + expr`, which reassociates anything containing an operator.
 */
export const PATHS = {
  selectedIndicator: () => resolveAsset('/images/UI/card/common/indicator-selected.webp'),
  identityFrameHighlight: () => resolveAsset('/images/UI/card/identity/legacy/hoverRing.webp'),
  egoFrame: () => resolveAsset('/images/UI/card/ego/legacy/frame.webp'),
  egoFrameHighlight: () => resolveAsset('/images/UI/card/ego/legacy/hoverRing.webp'),
  backupIndicator: () => resolveAsset('/images/UI/card/common/indicator-backup.webp'),
  uptieFrame: (rank: number, uptie: number) =>
    resolveAsset(`/images/UI/card/identity/frame-rank${String(rank)}-uptie${String(uptie)}.webp`),
  sinnerBG: (rank: number) =>
    resolveAsset(`/images/UI/card/identity/legacy/iconRing-rank${String(rank)}.webp`),
  egoInfoPanel: (attribute: string) =>
    resolveAsset(`/images/UI/card/ego/nameBg-${attribute.toLowerCase()}.webp`),

  identityInfoImage: (identityId: string, uptie: number) =>
    uptie < 3 || identityId.endsWith('01')
      ? resolveAsset(`/images/identity/${identityId}/${identityId}_normal_info.webp`)
      : resolveAsset(`/images/identity/${identityId}/${identityId}_gacksung_info.webp`),
  identityProfileImage: (identityId: string, uptie: number) =>
    uptie < 3
      ? resolveAsset(`/images/identity/${identityId}/${identityId}_normal_profile.webp`)
      : resolveAsset(`/images/identity/${identityId}/${identityId}_gacksung_profile.webp`),
  identityFallbackImage: (identityId: string) =>
    resolveAsset(`/images/identity/${identityId}/${identityId}_normal_info.webp`),
  identityDetailImage: (identityId: string, variant: 'gacksung' | 'normal') =>
    resolveAsset(`/images/identity/${identityId}/${identityId}_${variant}.webp`),
  skillImage: (identityId: string, skillId: string) =>
    resolveAsset(`/images/identity/${identityId}/${skillId}.webp`),
  /** iconID format: 5-digit identityId + skill number, so its directory is the first 5 characters */
  skillImageFromIconID: (iconID: string) => {
    const identityId = iconID.slice(0, 5)
    return resolveAsset(`/images/identity/${identityId}/${iconID}.webp`)
  },

  skillFrame: (attributeType: SkillAttributeType | undefined, skillTier: number) => {
    const attr = attributeType ?? 'NEUTRAL'
    const level = String(frameLevel(skillTier))
    return resolveAsset(`/images/UI/skill/frame-${attr.toLowerCase()}-tier${level}.webp`)
  },
  skillFrameBG: (attributeType: SkillAttributeType | undefined, skillTier: number) => {
    const attr = attributeType ?? 'NEUTRAL'
    const level = String(frameLevel(skillTier))
    return resolveAsset(`/images/UI/skill/frameBg-${attr.toLowerCase()}-tier${level}.webp`)
  },
  attackTypeFrame: (attributeType: SkillAttributeType) =>
    resolveAsset(`/images/UI/skill/attackType-${attributeType.toLowerCase()}.webp`),
  attackTypeFrameBG: (attributeType: SkillAttributeType) =>
    resolveAsset(`/images/UI/skill/attackTypeBg-${attributeType.toLowerCase()}.webp`),

  rarityIcon: (grade: number) =>
    resolveAsset(`/images/UI/identity/grade-rank${String(grade)}.webp`),
  identityPassiveCountIcon: () => resolveAsset('/images/UI/identity/passiveCount.webp'),
  attackLevelIcon: () => resolveAsset('/images/UI/identity/stat-attack.webp'),
  defenseLevelIcon: () => resolveAsset('/images/UI/identity/stat-defense.webp'),
  hpIcon: () => resolveAsset('/images/UI/identity/stat-hp.webp'),
  speedIcon: () => resolveAsset('/images/UI/identity/stat-speed.webp'),
  slashResistIcon: () => resolveAsset('/images/UI/identity/resist-slash.webp'),
  pierceResistIcon: () => resolveAsset('/images/UI/identity/resist-penetrate.webp'),
  bluntResistIcon: () => resolveAsset('/images/UI/identity/resist-hit.webp'),
  sanityIncIcon: () => resolveAsset('/images/UI/identity/sanity-inc.webp'),
  sanityDecIcon: () => resolveAsset('/images/UI/identity/sanity-dec.webp'),
  defenseTypeIcon: (defType: string) => {
    const filename = camelEnum(defType)
    return resolveAsset(`/images/UI/identity/defenseType-${filename}.webp`)
  },

  attackTypeIcon: (atkType: string) =>
    resolveAsset(`/images/UI/common/atkType-${atkType.toLowerCase()}.webp`),
  coinDescIcon: (coinIndex: number) =>
    resolveAsset(`/images/UI/common/coin-${String(coinIndex + 1)}.webp`),
  egoTierIcon: (tier: number) => resolveAsset(`/images/UI/common/tier-${String(tier)}.webp`),
  attackWeightIcon: () => resolveAsset('/images/UI/common/atkWeight.webp'),
  lockIcon: () => resolveAsset('/images/UI/common/lock.webp'),
  buttonBase: () => resolveAsset('/images/UI/common/button.webp'),
  buttonOnHover: () => resolveAsset('/images/UI/common/button-hover.webp'),
  buttonExpandImage: () => resolveAsset('/images/UI/common/button-expand.webp'),
  buttonSwapImage: () => resolveAsset('/images/UI/common/button-swap.webp'),
  buttonClose: () => resolveAsset('/images/UI/common/button-close.webp'),

  sinnerIcon: (sinner: string) => resolveAsset(`/images/icon/sinners/${sinner}.webp`),
  affinityIcon: (affinity: string) => {
    const sinName = AFFINITY_TO_SIN_NAME[affinity] || affinity
    return resolveAsset(`/images/icon/sin/${sinName}.webp`)
  },
  coinIcon: (coinType: 'C' | 'U') => {
    const iconName = coinType === 'U' ? 'superCoin' : 'coin'
    return resolveAsset(`/images/icon/${iconName}.webp`)
  },
  egoTypeIcon: (egoType: string) => resolveAsset(`/images/icon/ego/${egoType}.webp`),
  egoGiftIcon: (giftId: string) => resolveAsset(`/images/icon/egoGift/${giftId}.webp`),
  battleKeywordIcon: (iconIdOrKey: string) =>
    resolveAsset(`/images/icon/battleKeywords/${iconIdOrKey}.webp`),
  panicIcon: (panicType: string) => resolveAsset(`/images/icon/sanity/${panicType}.webp`),

  egoCg: (egoId: string, variant: 'awaken' | 'erosion') =>
    variant === 'erosion'
      ? resolveAsset(`/images/ego/${egoId}/${egoId}_e_cg.webp`)
      : resolveAsset(`/images/ego/${egoId}/${egoId}_cg.webp`),
  egoProfileImage: (egoId: string) =>
    resolveAsset(`/images/ego/${egoId}/${egoId}_awaken_profile.webp`),
  egoSkillImage: (egoId: string, skillType: 'awaken' | 'erosion') =>
    resolveAsset(`/images/ego/${egoId}/${egoId}_${skillType}_profile.webp`),
  egoRankIcon: (rank: string) => resolveAsset(`/images/UI/ego/rank-${rank.toLowerCase()}.webp`),

  egoGiftEnhancementIcon: (level: number) =>
    resolveAsset(`/images/UI/card/egoGift/enhancement-${String(level)}.webp`),
  egoGiftCostIcon: () => resolveAsset('/images/UI/card/egoGift/cost.webp'),
  egoGiftBackground: () => resolveAsset('/images/UI/card/egoGift/bg.webp'),
  egoGiftOnHover: () => resolveAsset('/images/UI/card/egoGift/hover.webp'),
  egoGiftEnhancedBackground: () => resolveAsset('/images/UI/card/egoGift/bg-enhanced1.webp'),
  egoGiftEnhanced2Background: () => resolveAsset('/images/UI/card/egoGift/bg-enhanced2.webp'),
  egoGiftTierEX: () => resolveAsset('/images/UI/card/egoGift/tier-ex.webp'),
  egoGiftSelectHighlight: () => resolveAsset('/images/UI/card/egoGift/focused.webp'),
  atkTypeGiftIcon: (atkType: string) =>
    resolveAsset(`/images/UI/card/egoGift/atkType-${atkType.toLowerCase()}.webp`),

  startBuffIcon: (baseId: number, version: number) =>
    resolveAsset(`/images/UI/startBuff/md${String(version)}/icon-${String(baseId)}.webp`),
  startBuffPane: (version: number) =>
    resolveAsset(`/images/UI/startBuff/md${String(version)}/pane.webp`),
  startBuffHighlight: (version: number) =>
    resolveAsset(`/images/UI/startBuff/md${String(version)}/highlight.webp`),
  startBuffMini: (version: number) =>
    resolveAsset(`/images/UI/startBuff/md${String(version)}/mini.webp`),
  startBuffMiniHighlight: (version: number) =>
    resolveAsset(`/images/UI/startBuff/md${String(version)}/miniHighlight.webp`),
  startBuffEnhancementBg: (level: 0 | 1 | 2, version: number) =>
    level === 0
      ? resolveAsset(`/images/UI/startBuff/md${String(version)}/enhancement-unselected.webp`)
      : resolveAsset(
          `/images/UI/startBuff/md${String(version)}/enhancement-selected-${String(level)}.webp`
        ),
  startBuffEnhancementOverlay: (version: number) =>
    resolveAsset(`/images/UI/startBuff/md${String(version)}/enhancement-selected.webp`),
  startBuffEnhancementIcon: (level: 0 | 1 | 2) =>
    level === 0
      ? resolveAsset('/images/UI/startBuff/enhancementIcon.webp')
      : resolveAsset(`/images/UI/card/egoGift/enhancement-${String(level)}.webp`),
  startBuffStarLight: () => resolveAsset('/images/UI/startBuff/starLight.webp'),

  themePackImage: (packId: string) => resolveAsset(`/images/themePack/${packId}.webp`),
  themePackHoverHighlight: () => resolveAsset('/images/UI/card/themePack/legacy/hover.webp'),
  themePackSelectHighlight: () => resolveAsset('/images/UI/card/themePack/legacy/focused.webp'),
  themePackExtremeHighlight: () =>
    resolveAsset('/images/UI/card/themePack/legacy/hover-extreme.webp'),
  featuredBossImage: (packId: string, portraitId: number | string) =>
    resolveAsset(`/images/featuredBoss/${packId}_${portraitId}.webp`),

  abEventImage: (eventId: string) => resolveAsset(`/images/abEvent/${eventId}.webp`),
  bannerImage: () => resolveAsset('/images/banner/MD.webp'),
  logo: () => resolveAsset('/images/logo/LCMC.webp'),
}

type PathTable = typeof PATHS
export type PathKey = keyof PathTable

export function path<K extends PathKey>(key: K, ...args: Parameters<PathTable[K]>): string {
  const build = PATHS[key] as (...args: Parameters<PathTable[K]>) => string
  return build(...args)
}

export const getSelectedIndicatorPath = (): string => path('selectedIndicator')
export const getIdentityFrameHighlightPath = (): string => path('identityFrameHighlight')
export const getEGOFramePath = (): string => path('egoFrame')
export const getEGOFrameHighlightPath = (): string => path('egoFrameHighlight')
export const getBackupIndicatorPath = (): string => path('backupIndicator')
export const getUptieFramePath = (rank: number, uptie = 4): string => path('uptieFrame', rank, uptie)
export const getSinnerBGPath = (rank: number): string => path('sinnerBG', rank)
export const getEGOInfoPanelPath = (attribute: string): string => path('egoInfoPanel', attribute)

export const getIdentityInfoImagePath = (identityId: string, identityUptie = 4): string =>
  path('identityInfoImage', identityId, identityUptie)
export const getIdentityProfileImagePath = (identityId: string, identityUptie = 4): string =>
  path('identityProfileImage', identityId, identityUptie)
export const getIdentityImageFallbackPath = (identityId: string): string =>
  path('identityFallbackImage', identityId)
export const getIdentityDetailImagePath = (
  identityId: string,
  variant: 'gacksung' | 'normal' = 'gacksung'
): string => path('identityDetailImage', identityId, variant)
export const getSkillImagePath = (identityId: string, skillId: string): string =>
  path('skillImage', identityId, skillId)
export const getSkillImagePathFromIconID = (iconID: string): string =>
  path('skillImageFromIconID', iconID)

export const getSkillFramePath = (
  attributeType: SkillAttributeType | undefined,
  skillTier: number
): string => path('skillFrame', attributeType, skillTier)
export const getSkillFrameBGPath = (
  attributeType: SkillAttributeType | undefined,
  skillTier: number
): string => path('skillFrameBG', attributeType, skillTier)
export const getAttackTypeFramePath = (attributeType: SkillAttributeType): string =>
  path('attackTypeFrame', attributeType)
export const getAttackTypeFrameBGPath = (attributeType: SkillAttributeType): string =>
  path('attackTypeFrameBG', attributeType)

export const getRarityIconPath = (grade: number): string => path('rarityIcon', grade)
export const getIdentityPassiveCountIconPath = (): string => path('identityPassiveCountIcon')
export const getAttackLevelIconPath = (): string => path('attackLevelIcon')
export const getDefenseLevelIconPath = (): string => path('defenseLevelIcon')
export const getHPIconPath = (): string => path('hpIcon')
export const getSpeedIconPath = (): string => path('speedIcon')
export const getSlashResistIconPath = (): string => path('slashResistIcon')
export const getPierceResistIconPath = (): string => path('pierceResistIcon')
export const getBluntResistIconPath = (): string => path('bluntResistIcon')
export const getSanityIncIconPath = (): string => path('sanityIncIcon')
export const getSanityDecIconPath = (): string => path('sanityDecIcon')
export const getDefenseTypeIconPath = (defType: string): string => path('defenseTypeIcon', defType)

export const getAttackTypeIconPath = (atkType: string): string => path('attackTypeIcon', atkType)
export const getCoinDescIconPath = (coinIndex: number): string => path('coinDescIcon', coinIndex)
export const getEGOTierIconPath = (tier: number): string => path('egoTierIcon', tier)
export const getAttackWeightIconPath = (): string => path('attackWeightIcon')
export const getLockIconPath = (): string => path('lockIcon')
export const getButtonBasePath = (): string => path('buttonBase')
export const getButtonOnHoverPath = (): string => path('buttonOnHover')
export const getButtonExpandImagePath = (): string => path('buttonExpandImage')
export const getButtonSwapImagePath = (): string => path('buttonSwapImage')
export const getButtonClosePath = (): string => path('buttonClose')

export const getSinnerIconPath = (sinner: string): string => path('sinnerIcon', sinner)
export const getAffinityIconPath = (affinity: string): string => path('affinityIcon', affinity)
export const getCoinIconPath = (coinType: 'C' | 'U'): string => path('coinIcon', coinType)
export const getEGOSmallRankIconPath = (rank: string): string => path('egoTypeIcon', rank)
export const getEGOTypeIconPath = (egoType: string): string => path('egoTypeIcon', egoType)
export const getEGOGiftIconPath = (giftId: string): string => path('egoGiftIcon', giftId)
export const getBattleKeywordIconPath = (iconIdOrKey: string): string =>
  path('battleKeywordIcon', iconIdOrKey)
export const getPanicIconPath = (panicType: string): string => path('panicIcon', panicType)

export const getEGOImagePath = (egoId: string): string => path('egoCg', egoId, 'awaken')
export const getEGODetailImagePath = (
  egoId: string,
  variant: 'awaken' | 'erosion' = 'awaken'
): string => path('egoCg', egoId, variant)
export const getEGOProfileImagePath = (egoId: string): string => path('egoProfileImage', egoId)
export const getEGOSkillImagePath = (egoId: string, skillType: 'awaken' | 'erosion'): string =>
  path('egoSkillImage', egoId, skillType)
export const getEGORankIconPath = (rank: string): string => path('egoRankIcon', rank)

export const getEGOGiftEnhancementIconPath = (level: number): string =>
  path('egoGiftEnhancementIcon', level)
export const getEGOGiftCostIconPath = (): string => path('egoGiftCostIcon')
export const getEGOGiftBackgroundPath = (): string => path('egoGiftBackground')
export const getEGOGiftOnHoverPath = (): string => path('egoGiftOnHover')
export const getEGOGiftEnhancedBackgroundPath = (): string => path('egoGiftEnhancedBackground')
export const getEGOGiftEnhanced2BackgroundPath = (): string => path('egoGiftEnhanced2Background')
export const getEGOGiftTierEXPath = (): string => path('egoGiftTierEX')
export const getEGOGiftSelectHighlightPath = (): string => path('egoGiftSelectHighlight')

export const getStartBuffIconPath = (baseId: number, version: number): string =>
  path('startBuffIcon', baseId, version)
export const getStartBuffPanePath = (version: number): string => path('startBuffPane', version)
export const getStartBuffHighlightPath = (version: number): string =>
  path('startBuffHighlight', version)
export const getStartBuffMiniPath = (version: number): string => path('startBuffMini', version)
export const getStartBuffMiniHighlightPath = (version: number): string =>
  path('startBuffMiniHighlight', version)
export const getStartBuffEnhancementBgPath = (level: 0 | 1 | 2, version: number): string =>
  path('startBuffEnhancementBg', level, version)
export const getStartBuffEnhancementOverlayPath = (version: number): string =>
  path('startBuffEnhancementOverlay', version)
export const getStartBuffEnhancementIconPath = (level: 0 | 1 | 2): string =>
  path('startBuffEnhancementIcon', level)
export const getStartBuffStarLightPath = (): string => path('startBuffStarLight')

export const getThemePackImagePath = (packId: string): string => path('themePackImage', packId)
export const getThemePackHoverHighlightPath = (): string => path('themePackHoverHighlight')
export const getThemePackSelectHighlightPath = (): string => path('themePackSelectHighlight')
export const getThemePackExtremeHighlightPath = (): string => path('themePackExtremeHighlight')
export const getFeaturedBossImagePath = (packId: string, portraitId: number | string): string =>
  path('featuredBossImage', packId, portraitId)

export const getAbEventImagePath = (eventId: string): string => path('abEventImage', eventId)
export const getBannerImagePath = (): string => path('bannerImage')
export const getLogoPath = (): string => path('logo')

type KeywordKind = 'affinity' | 'atkType' | 'egoGift' | 'battleKeyword'

function classifyKeyword(keyword: string): KeywordKind {
  if ((AFFINITIES as readonly string[]).includes(keyword)) return 'affinity'
  if ((ATK_TYPES as readonly string[]).includes(keyword.toUpperCase())) return 'atkType'
  if (/^\d{4}$/.test(keyword)) return 'egoGift'
  return 'battleKeyword'
}

export function getKeywordIconPath(keyword: string): string {
  switch (classifyKeyword(keyword)) {
    case 'affinity':
      return path('affinityIcon', keyword)
    case 'atkType':
      return path('atkTypeGiftIcon', keyword)
    case 'egoGift':
      return path('egoGiftIcon', keyword)
    case 'battleKeyword':
      return path('battleKeywordIcon', keyword)
  }
}
