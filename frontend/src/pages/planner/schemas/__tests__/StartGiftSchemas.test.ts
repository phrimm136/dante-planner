import { describe, it, expect } from 'vitest'
import { StartEgoGiftPoolsSchema } from '../StartGiftSchemas'

describe('StartEgoGiftPoolsSchema', () => {
  const validPools = {
    Combustion: ['9001', '9009', '9103'],
    Laceration: ['9005', '9029', '9108'],
    Vibration: ['9044', '9086', '9113'],
    Burst: ['9047', '9093', '9117'],
    Sinking: ['9041', '9054', '9124'],
    Breath: ['9046', '9051', '9129'],
    Charge: ['9043', '9052', '9134'],
    Slash: ['9032', '9194', '9140'],
    Penetrate: ['9030', '9198', '9145'],
    Hit: ['9012', '9202', '9150'],
  }

  it('accepts a valid pool map from startEgoGiftPools.json', () => {
    expect(StartEgoGiftPoolsSchema.safeParse(validPools).success).toBe(true)
  })

  it('rejects a numeric gift id', () => {
    expect(StartEgoGiftPoolsSchema.safeParse({ Combustion: [9001, 9009, 9103] }).success).toBe(
      false,
    )
  })

  it('rejects a gift id outside the 9{3 digits} range', () => {
    expect(StartEgoGiftPoolsSchema.safeParse({ Combustion: ['8001'] }).success).toBe(false)
  })

  it('rejects a gift id carrying an enhancement prefix', () => {
    expect(StartEgoGiftPoolsSchema.safeParse({ Combustion: ['19001'] }).success).toBe(false)
  })

  it('rejects a pool value that is not an array', () => {
    expect(StartEgoGiftPoolsSchema.safeParse({ Combustion: '9001' }).success).toBe(false)
  })
})
