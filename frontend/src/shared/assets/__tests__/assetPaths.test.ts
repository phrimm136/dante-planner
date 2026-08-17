import { describe, it, expect } from 'vitest'

import { resolveAsset } from '../assetManifest'
import * as assetPaths from '../assetPaths'
import { PATHS, path } from '../assetPaths'

type PathGetter = (...args: unknown[]) => string
type Case = [name: string, args: unknown[], expected: string]

const getters = assetPaths as unknown as Record<string, PathGetter>

const CASES: Case[] = [
  ['getSelectedIndicatorPath', [], '/images/UI/formation/selected.webp'],
  ['getIdentityFrameHighlightPath', [], '/images/UI/formation/identityFrameHighlight.webp'],
  ['getEGOFramePath', [], '/images/UI/formation/egoFrame.webp'],
  ['getEGOFrameHighlightPath', [], '/images/UI/formation/egoFrameHighlight.webp'],
  ['getBackupIndicatorPath', [], '/images/UI/formation/backup.webp'],
  ['getUptieFramePath', [1], '/images/UI/formation/1Rank4UptieFrame.webp'],
  ['getUptieFramePath', [3, 2], '/images/UI/formation/3Rank2UptieFrame.webp'],
  ['getSinnerBGPath', [2], '/images/UI/formation/2RankSinnerBG.webp'],
  ['getEGOInfoPanelPath', ['CRIMSON'], '/images/UI/formation/egoInfoPanelCRIMSON.webp'],

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

  ['getSkillFramePath', [undefined, 1], '/images/UI/skillFrame/NEUTRAL1.webp'],
  ['getSkillFramePath', ['CRIMSON', 2], '/images/UI/skillFrame/CRIMSON2.webp'],
  ['getSkillFramePath', ['CRIMSON', 9], '/images/UI/skillFrame/CRIMSON3.webp'],
  ['getSkillFramePath', ['CRIMSON', 0], '/images/UI/skillFrame/CRIMSON1.webp'],
  ['getSkillFrameBGPath', [undefined, 1], '/images/UI/skillFrame/NEUTRAL1BG.webp'],
  ['getSkillFrameBGPath', ['AZURE', 3], '/images/UI/skillFrame/AZURE3BG.webp'],
  ['getAttackTypeFramePath', ['CRIMSON'], '/images/UI/skillFrame/attackTypeCRIMSON.webp'],
  ['getAttackTypeFrameBGPath', ['CRIMSON'], '/images/UI/skillFrame/attackTypeBGCRIMSON.webp'],

  ['getRarityIconPath', [3], '/images/UI/identity/rarity3.webp'],
  ['getIdentityPassiveCountIconPath', [], '/images/UI/identity/passiveCount.webp'],
  ['getAttackLevelIconPath', [], '/images/UI/identity/attack.webp'],
  ['getDefenseLevelIconPath', [], '/images/UI/identity/defense.webp'],
  ['getHPIconPath', [], '/images/UI/identity/hp.webp'],
  ['getSpeedIconPath', [], '/images/UI/identity/speed.webp'],
  ['getSlashResistIconPath', [], '/images/UI/identity/SLASH.webp'],
  ['getPierceResistIconPath', [], '/images/UI/identity/PENETRATE.webp'],
  ['getBluntResistIconPath', [], '/images/UI/identity/HIT.webp'],
  ['getSanityIncIconPath', [], '/images/UI/identity/sanityInc.webp'],
  ['getSanityDecIconPath', [], '/images/UI/identity/sanityDec.webp'],
  ['getDefenseTypeIconPath', ['EVADE'], '/images/UI/identity/Evade.webp'],
  ['getDefenseTypeIconPath', ['CLASHABLE_GUARD'], '/images/UI/identity/ClashableGuard.webp'],

  ['getAttackTypeIconPath', ['slash'], '/images/UI/common/Slash.webp'],
  ['getAttackTypeIconPath', ['PENETRATE'], '/images/UI/common/Penetrate.webp'],
  ['getCoinDescIconPath', [0], '/images/UI/common/coin1.webp'],
  ['getCoinDescIconPath', [9], '/images/UI/common/coin10.webp'],
  ['getEGOTierIconPath', [5], '/images/UI/common/tier5.webp'],
  ['getAttackWeightIconPath', [], '/images/UI/common/atkWeight.webp'],
  ['getLockIconPath', [], '/images/UI/common/lock.webp'],
  ['getButtonBasePath', [], '/images/UI/common/button.webp'],
  ['getButtonOnHoverPath', [], '/images/UI/common/buttonOnHover.webp'],
  ['getButtonExpandImagePath', [], '/images/UI/common/buttonExpandImage.webp'],
  ['getButtonSwapImagePath', [], '/images/UI/common/buttonSwapImage.webp'],

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
  ['getEGOProfileImagePath', ['20101'], '/images/ego/20101/20101_awaken_profile.webp'],
  ['getEGOSkillImagePath', ['20101', 'awaken'], '/images/ego/20101/20101_awaken_profile.webp'],
  ['getEGOSkillImagePath', ['20102', 'erosion'], '/images/ego/20102/20102_erosion_profile.webp'],
  ['getEGORankIconPath', ['ZAYIN'], '/images/UI/ego/ZAYIN.webp'],

  ['getEGOGiftEnhancementIconPath', [1], '/images/UI/egoGift/enhancement1.webp'],
  ['getEGOGiftEnhancementIconPath', [2], '/images/UI/egoGift/enhancement2.webp'],
  ['getEGOGiftCostIconPath', [], '/images/UI/egoGift/cost.webp'],
  ['getEGOGiftBackgroundPath', [], '/images/UI/egoGift/bg.webp'],
  ['getEGOGiftOnHoverPath', [], '/images/UI/egoGift/onHover.webp'],
  ['getEGOGiftEnhancedBackgroundPath', [], '/images/UI/egoGift/bgEnhanced.webp'],
  ['getEGOGiftEnhanced2BackgroundPath', [], '/images/UI/egoGift/bgEnhanced2.webp'],
  ['getEGOGiftTierEXPath', [], '/images/UI/egoGift/tierEX.webp'],
  ['getEGOGiftSelectHighlightPath', [], '/images/UI/egoGift/onSelect.webp'],

  ['getStartBuffIconPath', [100, 7], '/images/UI/MD7/StartBuffIcon_100.webp'],
  ['getStartBuffPanePath', [7], '/images/UI/MD7/startBuffPane.webp'],
  ['getStartBuffHighlightPath', [7], '/images/UI/MD7/startBuffHighlight.webp'],
  ['getStartBuffMiniPath', [7], '/images/UI/MD7/startBuffMini.webp'],
  ['getStartBuffMiniHighlightPath', [7], '/images/UI/MD7/startBuffMiniHighlight.webp'],
  ['getStartBuffEnhancementBgPath', [0, 7], '/images/UI/MD7/startBuffEnhancementUnselected.webp'],
  ['getStartBuffEnhancementBgPath', [1, 6], '/images/UI/MD6/startBuffEnhancement1Selected.webp'],
  ['getStartBuffEnhancementBgPath', [2, 6], '/images/UI/MD6/startBuffEnhancement2Selected.webp'],
  ['getStartBuffEnhancementOverlayPath', [7], '/images/UI/MD7/startBuffEnhancementSelected.webp'],
  ['getStartBuffEnhancementIconPath', [0], '/images/UI/MD/startBuffEnhancementIcon.webp'],
  ['getStartBuffEnhancementIconPath', [2], '/images/UI/egoGift/enhancement2.webp'],
  ['getStartBuffStarLightPath', [], '/images/UI/MD/starLight.webp'],

  ['getThemePackImagePath', ['1001'], '/images/themePack/1001.webp'],
  ['getThemePackHoverHighlightPath', [], '/images/UI/themePack/onHover.webp'],
  ['getThemePackSelectHighlightPath', [], '/images/UI/themePack/onSelect.webp'],
  ['getThemePackExtremeHighlightPath', [], '/images/UI/themePack/extremeHighlight.webp'],
  ['getFeaturedBossImagePath', ['1001', 91001], '/images/featuredBoss/1001_91001.webp'],
  ['getFeaturedBossImagePath', ['1001', '91001'], '/images/featuredBoss/1001_91001.webp'],

  ['getAbEventImagePath', ['901001'], '/images/abEvent/901001.webp'],
  ['getBannerImagePath', [], '/images/banner/MD.webp'],
  ['getLogoPath', [], '/images/logo/LCMC.webp'],

  ['getKeywordIconPath', ['CRIMSON'], '/images/icon/sin/Wrath.webp'],
  ['getKeywordIconPath', ['Slash'], '/images/UI/egoGift/Slash.webp'],
  ['getKeywordIconPath', ['Penetrate'], '/images/UI/egoGift/Penetrate.webp'],
  ['getKeywordIconPath', ['Hit'], '/images/UI/egoGift/Hit.webp'],
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
