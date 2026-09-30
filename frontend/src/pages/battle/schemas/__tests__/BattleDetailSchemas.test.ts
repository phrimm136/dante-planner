import { describe, expect, it } from 'vitest'

import assistant400038 from '@static/data/assistant/400038.json'
import enemy1519 from '@static/data/enemy/1519.json'
import enemy91059 from '@static/data/enemy/91059.json'
import sinnerEvent10101 from '@static/data/sinnerEvent/10101.json'
import { AssistantDataSchema, EnemyDataSchema, SinnerEventDataSchema } from '../BattleDetailSchemas'

describe('battle unit owner type', () => {
  it('reads ABNORMALITY on the phase and part of enemy 1519', () => {
    const [phase] = EnemyDataSchema.parse(enemy1519).units['1519']?.phases ?? []
    expect(phase?.ownerType).toBe('ABNORMALITY')
    expect(phase?.parts?.map((part) => part.ownerType)).toEqual(['ABNORMALITY'])
  })

  it('reads ENEMY on the phase of enemy 91059', () => {
    const [phase] = EnemyDataSchema.parse(enemy91059).units['91059']?.phases ?? []
    expect(phase?.ownerType).toBe('ENEMY')
  })

  it('parses assistant 400038 and event sinner 10101 without an owner type', () => {
    const [assistant] = AssistantDataSchema.parse(assistant400038).units['400038']?.phases ?? []
    const [sinner] = SinnerEventDataSchema.parse(sinnerEvent10101).units['10101']?.phases ?? []
    expect(assistant?.ownerType).toBeUndefined()
    expect(sinner?.ownerType).toBeUndefined()
  })

  it('rejects an owner type outside the client enum', () => {
    const [phase] = enemy91059.units['91059'].phases
    const raw = {
      ...enemy91059,
      units: {
        '91059': { ...enemy91059.units['91059'], phases: [{ ...phase, ownerType: 'SINNER' }] },
      },
    }
    expect(EnemyDataSchema.safeParse(raw).success).toBe(false)
  })
})
