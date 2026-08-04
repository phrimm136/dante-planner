/**
 * Test utilities for LimbusPlanner frontend
 * Import from this file to get render with providers and test utilities
 */
export { createTestFilterStore } from './filterStore'
export { createTestQueryClient } from './queryClient'
export { createTestRouter } from './router'
export { renderWithProviders } from './renderWithProviders'
export {
  countRenders,
  getRenderCount,
  recordRender,
  rendersSince,
  RenderProbe,
  resetRenderCounts,
  snapshotRenderCounts,
} from './renderCounter'

// Re-export Testing Library utilities
export * from '@testing-library/react'
export { userEvent } from '@testing-library/user-event'
