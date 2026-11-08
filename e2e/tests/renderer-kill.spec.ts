import { test, expect } from '@playwright/test'
import { onlyRendererPid } from '../src/rendererKill'

const BROWSER = { type: 'browser', id: 100 }
const GPU = { type: 'GPU', id: 101 }

test('no renderer is refused with the count', () => {
  expect(() => onlyRendererPid([BROWSER, GPU])).toThrow('found 0')
})

test('the only renderer is the one to kill', () => {
  expect(onlyRendererPid([BROWSER, GPU, { type: 'renderer', id: 200 }])).toBe(200)
})

test('two renderers are refused with the count', () => {
  expect(() =>
    onlyRendererPid([BROWSER, { type: 'renderer', id: 200 }, { type: 'renderer', id: 201 }]),
  ).toThrow('found 2')
})
