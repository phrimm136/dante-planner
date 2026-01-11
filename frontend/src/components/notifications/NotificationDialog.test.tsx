/**
 * NotificationDialog.test.tsx
 *
 * Tests for NotificationDialog component.
 * Validates dialog open, notification list display, mark all as read, and dismiss actions.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { NotificationInboxResponse } from '@/types/NotificationTypes'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: vi.fn(() => vi.fn()),
}))

vi.mock('@/hooks/useNotificationsQuery', () => ({
  useNotificationsQuery: vi.fn(),
}))

vi.mock('@/hooks/useMarkReadMutation', () => ({
  useMarkReadMutation: vi.fn(),
}))

vi.mock('@/hooks/useDeleteNotificationMutation', () => ({
  useDeleteNotificationMutation: vi.fn(),
}))

import { useNavigate } from '@tanstack/react-router'
import { useNotificationsQuery } from '@/hooks/useNotificationsQuery'
import { useMarkReadMutation } from '@/hooks/useMarkReadMutation'
import { useDeleteNotificationMutation } from '@/hooks/useDeleteNotificationMutation'
import { NotificationDialog } from './NotificationDialog'

const mockNavigate = vi.fn()
const mockMarkReadMutate = vi.fn()
const mockDeleteMutate = vi.fn()

const mockInboxData: NotificationInboxResponse = {
  notifications: [
    {
      id: 1,
      contentId: 'planner-123',
      notificationType: 'PLANNER_RECOMMENDED',
      read: false,
      createdAt: new Date('2025-01-10T10:00:00Z').toISOString(),
      readAt: null,
    },
    {
      id: 2,
      contentId: 'planner-456',
      notificationType: 'COMMENT_RECEIVED',
      read: true,
      createdAt: new Date('2025-01-09T15:00:00Z').toISOString(),
      readAt: new Date('2025-01-09T16:00:00Z').toISOString(),
    },
    {
      id: 3,
      contentId: 'comment-789',
      notificationType: 'REPLY_RECEIVED',
      read: false,
      createdAt: new Date('2025-01-08T12:00:00Z').toISOString(),
      readAt: null,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 3,
  totalPages: 1,
}

describe('NotificationDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useNavigate).mockReturnValue(mockNavigate)
    vi.mocked(useNotificationsQuery).mockReturnValue({
      data: mockInboxData,
      isLoading: false,
    } as ReturnType<typeof useNotificationsQuery>)
    vi.mocked(useMarkReadMutation).mockReturnValue({
      mutate: mockMarkReadMutate,
      isPending: false,
    } as ReturnType<typeof useMarkReadMutation>)
    vi.mocked(useDeleteNotificationMutation).mockReturnValue({
      mutate: mockDeleteMutate,
      isPending: false,
    } as ReturnType<typeof useDeleteNotificationMutation>)
  })

  describe('dialog open/close', () => {
    it('renders dialog when open is true', () => {
      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />)

      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(screen.getByText('Notifications')).toBeInTheDocument()
    })

    it('does not render dialog content when open is false', () => {
      const onOpenChange = vi.fn()
      render(<NotificationDialog open={false} onOpenChange={onOpenChange} />)

      expect(screen.queryByText('Notifications')).not.toBeInTheDocument()
    })
  })

  describe('notification list display', () => {
    it('displays all notifications from query', () => {
      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />)

      expect(screen.getByText('Your planner is now recommended!')).toBeInTheDocument()
      expect(screen.getByText('New comment on your planner')).toBeInTheDocument()
      expect(screen.getByText('Someone replied to your comment')).toBeInTheDocument()
    })

    it('shows loading state when isLoading is true', () => {
      vi.mocked(useNotificationsQuery).mockReturnValue({
        data: undefined,
        isLoading: true,
      } as ReturnType<typeof useNotificationsQuery>)

      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />)

      expect(screen.getByText('Loading notifications...')).toBeInTheDocument()
    })

    it('shows empty state when no notifications', () => {
      vi.mocked(useNotificationsQuery).mockReturnValue({
        data: { notifications: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
        isLoading: false,
      } as ReturnType<typeof useNotificationsQuery>)

      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />)

      expect(screen.getByText('No notifications')).toBeInTheDocument()
    })

    it('does not show "Mark all as read" button when no notifications', () => {
      vi.mocked(useNotificationsQuery).mockReturnValue({
        data: { notifications: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
        isLoading: false,
      } as ReturnType<typeof useNotificationsQuery>)

      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />)

      expect(screen.queryByRole('button', { name: /mark all as read/i })).not.toBeInTheDocument()
    })
  })

  describe('mark all as read', () => {
    it('shows "Mark all as read" button when notifications exist', () => {
      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />)

      expect(screen.getByRole('button', { name: /mark all as read/i })).toBeInTheDocument()
    })

    it('disables "Mark all as read" button when all notifications are read', () => {
      vi.mocked(useNotificationsQuery).mockReturnValue({
        data: {
          notifications: [
            {
              id: 1,
              contentId: 'planner-123',
              notificationType: 'PLANNER_RECOMMENDED',
              read: true,
              createdAt: new Date('2025-01-10T10:00:00Z').toISOString(),
              readAt: new Date('2025-01-10T11:00:00Z').toISOString(),
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
        isLoading: false,
      } as ReturnType<typeof useNotificationsQuery>)

      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />)

      const button = screen.getByRole('button', { name: /mark all as read/i })
      expect(button).toBeDisabled()
    })

    it('calls mutation for all unread notifications when clicked', async () => {
      const user = userEvent.setup()
      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />)

      const button = screen.getByRole('button', { name: /mark all as read/i })
      await user.click(button)

      expect(mockMarkReadMutate).toHaveBeenCalledWith(1)
      expect(mockMarkReadMutate).toHaveBeenCalledWith(3)
      expect(mockMarkReadMutate).not.toHaveBeenCalledWith(2)
      expect(mockMarkReadMutate).toHaveBeenCalledTimes(2)
    })
  })

  describe('notification item interactions', () => {
    it('navigates to content when notification is clicked', async () => {
      const user = userEvent.setup()
      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />)

      const notification = screen.getByText('Your planner is now recommended!')
      await user.click(notification)

      expect(mockNavigate).toHaveBeenCalledWith({
        to: '/planner/md/planner-123',
      })
      expect(onOpenChange).toHaveBeenCalledWith(false)
    })

    it('marks notification as read when clicked if unread', async () => {
      const user = userEvent.setup()
      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />)

      const notification = screen.getByText('Your planner is now recommended!')
      await user.click(notification)

      expect(mockMarkReadMutate).toHaveBeenCalledWith(1)
    })

    it('dismisses notification when dismiss button is clicked', async () => {
      const user = userEvent.setup()
      const onOpenChange = vi.fn()
      render(<NotificationDialog open={true} onOpenChange={onOpenChange} />)

      const dismissButtons = screen.getAllByLabelText('Dismiss notification')
      await user.click(dismissButtons[0])

      expect(mockDeleteMutate).toHaveBeenCalledWith(1)
      expect(onOpenChange).not.toHaveBeenCalled()
    })
  })
})
