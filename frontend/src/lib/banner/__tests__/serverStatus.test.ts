import { describe, it, expect, beforeEach } from 'vitest'

import { useServerStatusStore } from '../serverStatus'

describe('useServerStatusStore', () => {
  beforeEach(() => {
    useServerStatusStore.setState({ status: 'ok' })
  })

  it('starts ok and holds the last status set', () => {
    expect(useServerStatusStore.getState().status).toBe('ok')

    useServerStatusStore.getState().setStatus('down')

    expect(useServerStatusStore.getState().status).toBe('down')
  })
})
