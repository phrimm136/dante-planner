import { describe, it, expect } from 'vitest'

import { resolveAsset } from '../assetManifest'
import * as assetPaths from '../assetPaths'
import { PATHS, path } from '../assetPaths'

type PathGetter = (...args: unknown[]) => string
type Case = [name: string, args: unknown[], expected: string]

const getters = assetPaths as unknown as Record<string, PathGetter>

const CASES: Case[] = [
  ['getSelectedIndicatorPath', [], '/images/UI/card/common/indicator-selected.webp'],
  ['getIdentityFrameHighlightPath', [], '/images/UI/card/identity/legacy/hoverRing.webp'],
  ['getEGOFramePath', [], '/images/UI/card/ego/legacy/frame.webp'],
  ['getEGOFrameHighlightPath', [], '/images/UI/card/ego/legacy/hoverRing.webp'],
  ['getBackupIndicatorPath', [], '/images/UI/card/common/indicator-backup.webp'],
  ['getUptieFramePath', [1], '/images/UI/card/identity/frame-rank1-uptie4.webp'],
  ['getUptieFramePath', [3, 2], '/images/UI/card/identity/frame-rank3-uptie2.webp'],
  ['getSinnerBGPath', [2], '/images/UI/card/identity/legacy/iconRing-rank2.webp'],
  ['getEGOInfoPanelPath', ['CRIMSON'], '/images/UI/card/ego/nameBg-crimson.webp'],

  ['getIdentityInfoImagePath', ['10102'], '/images/identity/10102/10102_gacksung_info.webp'],
  ['getIdentityInfoImagePath', ['10102', 2], '/images/identity/10102/10102_normal_info.webp'],
  ['getIdentityInfoImagePath', ['10101'], '/images/identity/10101/10101_normal_info.webp'],
  ['getIdentityProfileImagePath', ['10102'], '/images/identity/10102/10102_gacksung_profile.webp'],
  ['getIdentityProfileImagePath', ['10102', 2], '/images/identity/10102/10102_normal_profile.webp'],
  ['getIdentityImageFallbackPath', ['10102'], '/images/identity/10102/10102_normal_info.webp'],
  ['getIdentityDetailImagePath', ['10102'], '/images/identity/10102/10102_gacksung.webp'],
  ['getIdentityDetailImagePath', ['10102', 'normal'], '/images/identity/10102/10102_normal.webp'],
  ['getSkillImagePath', ['10102', '1010201'], '/images/identity/10102/1010201.webp'],
  ['getSkillImagePathFromIconID', ['1010201'], '/images/identity/10102/1010201.webp'],
  ['getSkillImagePathFromIconID', ['1010204_4'], '/images/identity/10102/1010204_4.webp'],

  ['getSkillFramePath', [undefined, 1], '/images/UI/skill/frame-neutral-tier1.webp'],
  ['getSkillFramePath', ['CRIMSON', 2], '/images/UI/skill/frame-crimson-tier2.webp'],
  ['getSkillFramePath', ['CRIMSON', 9], '/images/UI/skill/frame-crimson-tier3.webp'],
  ['getSkillFramePath', ['CRIMSON', 0], '/images/UI/skill/frame-crimson-tier1.webp'],
  ['getSkillFrameBGPath', [undefined, 1], '/images/UI/skill/frameBg-neutral-tier1.webp'],
  ['getSkillFrameBGPath', ['AZURE', 3], '/images/UI/skill/frameBg-azure-tier3.webp'],
  ['getAttackTypeFramePath', ['CRIMSON'], '/images/UI/skill/attackType-crimson.webp'],
  ['getAttackTypeFrameBGPath', ['CRIMSON'], '/images/UI/skill/attackTypeBg-crimson.webp'],

  ['getRarityIconPath', [3], '/images/UI/identity/grade-rank3.webp'],
  ['getIdentityPassiveCountIconPath', [], '/images/UI/identity/passiveCount.webp'],
  ['getAttackLevelIconPath', [], '/images/UI/identity/stat-attack.webp'],
  ['getDefenseLevelIconPath', [], '/images/UI/identity/stat-defense.webp'],
  ['getHPIconPath', [], '/images/UI/identity/stat-hp.webp'],
  ['getSpeedIconPath', [], '/images/UI/identity/stat-speed.webp'],
  ['getSlashResistIconPath', [], '/images/UI/identity/resist-slash.webp'],
  ['getPierceResistIconPath', [], '/images/UI/identity/resist-penetrate.webp'],
  ['getBluntResistIconPath', [], '/images/UI/identity/resist-hit.webp'],
  ['getSanityIncIconPath', [], '/images/UI/identity/sanity-inc.webp'],
  ['getSanityDecIconPath', [], '/images/UI/identity/sanity-dec.webp'],
  ['getDefenseTypeIconPath', ['EVADE'], '/images/UI/identity/defenseType-evade.webp'],
  ['getDefenseTypeIconPath', ['CLASHABLE_GUARD'], '/images/UI/identity/defenseType-clashableGuard.webp'],

  ['getAttackTypeIconPath', ['slash'], '/images/UI/common/atkType-slash.webp'],
  ['getAttackTypeIconPath', ['PENETRATE'], '/images/UI/common/atkType-penetrate.webp'],
  ['getCoinDescIconPath', [0], '/images/UI/common/coin-1.webp'],
  ['getCoinDescIconPath', [9], '/images/UI/common/coin-10.webp'],
  ['getEGOTierIconPath', [5], '/images/UI/common/tier-5.webp'],
  ['getAttackWeightIconPath', [], '/images/UI/common/atkWeight.webp'],
  ['getLockIconPath', [], '/images/UI/common/lock.webp'],
  ['getButtonBasePath', [], '/images/UI/common/button.webp'],
  ['getButtonOnHoverPath', [], '/images/UI/common/button-hover.webp'],
  ['getButtonExpandImagePath', [], '/images/UI/common/button-expand.webp'],
  ['getButtonSwapImagePath', [], '/images/UI/common/button-swap.webp'],
  ['getButtonClosePath', [], '/images/UI/common/button-close.webp'],

  ['getSinnerIconPath', ['DonQuixote'], '/images/icon/sinners/DonQuixote.webp'],
  ['getAffinityIconPath', ['CRIMSON'], '/images/icon/sin/Wrath.webp'],
  ['getAffinityIconPath', ['SCARLET'], '/images/icon/sin/Lust.webp'],
  ['getAffinityIconPath', ['AMBER'], '/images/icon/sin/Sloth.webp'],
  ['getAffinityIconPath', ['SHAMROCK'], '/images/icon/sin/Gluttony.webp'],
  ['getAffinityIconPath', ['AZURE'], '/images/icon/sin/Gloom.webp'],
  ['getAffinityIconPath', ['INDIGO'], '/images/icon/sin/Pride.webp'],
  ['getAffinityIconPath', ['VIOLET'], '/images/icon/sin/Envy.webp'],
  ['getAffinityIconPath', ['Envy'], '/images/icon/sin/Envy.webp'],
  ['getCoinIconPath', ['C'], '/images/icon/coin.webp'],
  ['getCoinIconPath', ['U'], '/images/icon/superCoin.webp'],
  ['getEGOSmallRankIconPath', ['ZAYIN'], '/images/icon/ego/ZAYIN.webp'],
  ['getEGOTypeIconPath', ['ALEPH'], '/images/icon/ego/ALEPH.webp'],
  ['getEGOGiftIconPath', ['9001'], '/images/icon/egoGift/9001.webp'],
  [
    'getBattleKeywordIconPath',
    ['AStrokeOfDeath'],
    '/images/icon/battleKeywords/AStrokeOfDeath.webp',
  ],
  ['getPanicIconPath', [1014], '/images/icon/sanity/1014.webp'],

  ['getEGOImagePath', ['20101'], '/images/ego/20101/20101_cg.webp'],
  ['getEGODetailImagePath', ['20101'], '/images/ego/20101/20101_cg.webp'],
  ['getEGODetailImagePath', ['20103', 'erosion'], '/images/ego/20103/20103_e_cg.webp'],
  ['getEGOProfileImagePath', ['20101'], '/images/ego/20101/20101_awaken_profile.webp'],
  ['getEGOSkillImagePath', ['20101', 'awaken'], '/images/ego/20101/20101_awaken_profile.webp'],
  ['getEGOSkillImagePath', ['20102', 'erosion'], '/images/ego/20102/20102_erosion_profile.webp'],
  ['getEGORankIconPath', ['ZAYIN'], '/images/UI/ego/rank-zayin.webp'],

  ['getEGOGiftEnhancementIconPath', [1], '/images/UI/card/egoGift/enhancement-1.webp'],
  ['getEGOGiftEnhancementIconPath', [2], '/images/UI/card/egoGift/enhancement-2.webp'],
  ['getEGOGiftCostIconPath', [], '/images/UI/card/egoGift/cost.webp'],
  ['getEGOGiftBackgroundPath', [], '/images/UI/card/egoGift/bg.webp'],
  ['getEGOGiftOnHoverPath', [], '/images/UI/card/egoGift/hover.webp'],
  ['getEGOGiftEnhancedBackgroundPath', [], '/images/UI/card/egoGift/bg-enhanced1.webp'],
  ['getEGOGiftEnhanced2BackgroundPath', [], '/images/UI/card/egoGift/bg-enhanced2.webp'],
  ['getEGOGiftTierEXPath', [], '/images/UI/card/egoGift/tier-ex.webp'],
  ['getEGOGiftSelectHighlightPath', [], '/images/UI/card/egoGift/focused.webp'],

  ['getStartBuffIconPath', [100, 7], '/images/UI/startBuff/md7/icon-100.webp'],
  ['getStartBuffPanePath', [7], '/images/UI/startBuff/md7/pane.webp'],
  ['getStartBuffHighlightPath', [7], '/images/UI/startBuff/md7/highlight.webp'],
  ['getStartBuffMiniPath', [7], '/images/UI/startBuff/md7/mini.webp'],
  ['getStartBuffMiniHighlightPath', [7], '/images/UI/startBuff/md7/miniHighlight.webp'],
  ['getStartBuffEnhancementBgPath', [0, 7], '/images/UI/startBuff/md7/enhancement-unselected.webp'],
  ['getStartBuffEnhancementBgPath', [1, 6], '/images/UI/startBuff/md6/enhancement-selected-1.webp'],
  ['getStartBuffEnhancementBgPath', [2, 6], '/images/UI/startBuff/md6/enhancement-selected-2.webp'],
  ['getStartBuffEnhancementOverlayPath', [7], '/images/UI/startBuff/md7/enhancement-selected.webp'],
  ['getStartBuffEnhancementIconPath', [0], '/images/UI/startBuff/enhancementIcon.webp'],
  ['getStartBuffEnhancementIconPath', [2], '/images/UI/card/egoGift/enhancement-2.webp'],
  ['getStartBuffStarLightPath', [], '/images/UI/startBuff/starLight.webp'],

  ['getThemePackImagePath', ['1001'], '/images/themePack/1001.webp'],
  ['getThemePackHoverHighlightPath', [], '/images/UI/card/themePack/legacy/hover.webp'],
  ['getThemePackSelectHighlightPath', [], '/images/UI/card/themePack/legacy/focused.webp'],
  ['getThemePackExtremeHighlightPath', [], '/images/UI/card/themePack/legacy/hover-extreme.webp'],
  ['getFeaturedBossImagePath', ['1001', 91001], '/images/featuredBoss/1001_91001.webp'],
  ['getFeaturedBossImagePath', ['1001', '91001'], '/images/featuredBoss/1001_91001.webp'],

  ['getAbEventImagePath', ['901001'], '/images/abEvent/901001.webp'],
  ['getBannerImagePath', [], '/images/banner/MD.webp'],
  ['getLogoPath', [], '/images/logo/LCMC.webp'],

  ['getKeywordIconPath', ['CRIMSON'], '/images/icon/sin/Wrath.webp'],
  ['getKeywordIconPath', ['Slash'], '/images/UI/card/egoGift/atkType-slash.webp'],
  ['getKeywordIconPath', ['Penetrate'], '/images/UI/card/egoGift/atkType-penetrate.webp'],
  ['getKeywordIconPath', ['Hit'], '/images/UI/card/egoGift/atkType-hit.webp'],
  ['getKeywordIconPath', ['9001'], '/images/icon/egoGift/9001.webp'],
  ['getKeywordIconPath', ['AStrokeOfDeath'], '/images/icon/battleKeywords/AStrokeOfDeath.webp'],
]

describe('asset path getters', () => {
  it.each(CASES)('%s(%j) → %s', (name, args, expected) => {
    const getter = getters[name]
    expect(getter).toBeDefined()
    expect(getter?.(...args)).toBe(resolveAsset(expected))
  })

  it('pins every exported getter', () => {
    const exported = Object.keys(getters).filter((key) => key.startsWith('get'))
    expect([...new Set(CASES.map(([name]) => name))].sort()).toEqual(exported.sort())
  })

  it('ships every expected path as a real static asset', () => {
    const missing = [...new Set(CASES.map(([, , expected]) => expected))].filter(
      (expected) => resolveAsset(expected) === expected,
    )
    expect(missing).toEqual([])
  })
})

describe('PATHS table', () => {
  it('reaches every entry through the accessor', () => {
    for (const key of Object.keys(PATHS) as (keyof typeof PATHS)[]) {
      expect(typeof PATHS[key]).toBe('function')
    }
    expect(path('logo')).toBe(assetPaths.getLogoPath())
    expect(path('sinnerIcon', 'DonQuixote')).toBe(assetPaths.getSinnerIconPath('DonQuixote'))
  })

  it('serves each merged duplicate pair from one entry', () => {
    expect(assetPaths.getEGOImagePath('20101')).toBe(assetPaths.getEGODetailImagePath('20101'))
    expect(assetPaths.getEGOSmallRankIconPath('ZAYIN')).toBe(assetPaths.getEGOTypeIconPath('ZAYIN'))
  })
})
