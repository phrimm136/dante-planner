/**
 * NotificationDialog.test.tsx
 *
 * Tests for NotificationDialog component.
 * Validates dialog open/close behavior.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React, { Suspense } from 'react'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: vi.fn(() => vi.fn()),
}))

vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: () => {} },
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'notifications.title': 'Notifications',
        'notifications.empty': 'No notifications',
        'notifications.clearAll': 'Clear All',
      }
      return translations[key] ?? key
    },
    i18n: {
      language: 'en',
    },
  }),
}))

vi.mock('@/hooks/useNotificationsQuery', () => ({
  useNotificationsQuery: vi.fn(),
  notificationQueryKeys: { all: ['notifications'] },
}))

vi.mock('@/hooks/useDeleteNotificationMutation', () => ({
  useDeleteNotificationMutation: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
  })),
}))

import { useNotificationsQuery } from '@/hooks/useNotificationsQuery'
import { NotificationDialog } from './NotificationDialog'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={<div>Loading...</div>}>{children}</Suspense>
      </QueryClientProvider>
    )
  }
}

describe('NotificationDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useNotificationsQuery).mockReturnValue({
      notifications: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    } as ReturnType<typeof useNotificationsQuery>)
  })

  describe('dialog open/close', () => {
    it('renders dialog when open is true', () => {
      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />, {
        wrapper: createWrapper(),
      })

      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(screen.getByText('Notifications')).toBeInTheDocument()
    })

    it('does not render dialog content when open is false', () => {
      const onOpenChange = vi.fn()
      render(<NotificationDialog open={false} onOpenChange={onOpenChange} />, {
        wrapper: createWrapper(),
      })

      expect(screen.queryByText('Notifications')).not.toBeInTheDocument()
    })
  })

  describe('empty state', () => {
    it('shows empty state when no notifications', () => {
      vi.mocked(useNotificationsQuery).mockReturnValue({
        notifications: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      } as ReturnType<typeof useNotificationsQuery>)

      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />, {
        wrapper: createWrapper(),
      })

      expect(screen.getByText('No notifications')).toBeInTheDocument()
    })

    it('does not show "Clear All" button when no notifications', () => {
      vi.mocked(useNotificationsQuery).mockReturnValue({
        notifications: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      } as ReturnType<typeof useNotificationsQuery>)

      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />, {
        wrapper: createWrapper(),
      })

      expect(screen.queryByRole('button', { name: /clear all/i })).not.toBeInTheDocument()
    })
  })

  describe('with notifications', () => {
    it('shows "Clear All" button when notifications exist', () => {
      vi.mocked(useNotificationsQuery).mockReturnValue({
        notifications: [
          {
            id: '550e8400-e29b-41d4-a716-446655440000',
            plannerId: '550e8400-e29b-41d4-a716-446655440001',
            notificationType: 'PLANNER_RECOMMENDED',
            createdAt: new Date('2025-01-10T10:00:00Z').toISOString(),
            commentPublicId: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      } as unknown as ReturnType<typeof useNotificationsQuery>)

      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />, {
        wrapper: createWrapper(),
      })

      expect(screen.getByRole('button', { name: /clear all/i })).toBeInTheDocument()
    })
  })
})
