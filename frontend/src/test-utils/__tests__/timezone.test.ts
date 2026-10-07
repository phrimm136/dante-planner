import { expect, it } from 'vitest'

it('runs every test in UTC whatever the host time zone', () => {
  expect(new Date().getTimezoneOffset()).toBe(0)
})
