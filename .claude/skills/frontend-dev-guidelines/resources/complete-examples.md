# Complete Examples

Full working examples combining modern patterns: explicit props typing, lazy loading, Suspense, useSuspenseQuery, shadcn/ui + Tailwind styling, TanStack Router, and error handling with sonner.

---

## Example 1: Complete Modern Component

Combines: Explicit props, useSuspenseQuery, shadcn/ui, Tailwind, sonner

```typescript
/**
 * User profile display component
 * Demonstrates modern patterns with Suspense and TanStack Query
 */
import { useState } from 'react'
import { useSuspenseQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { userApi } from '../api/userApi'
import type { User } from '@/types/UserTypes'

interface UserProfileProps {
  userId: string
  onUpdate?: () => void
}

export function UserProfile({ userId, onUpdate }: UserProfileProps) {
  const queryClient = useQueryClient()
  const [isEditing, setIsEditing] = useState(false)

  // Suspense query - no isLoading needed!
  const { data: user } = useSuspenseQuery({
    queryKey: ['user', userId],
    queryFn: () => userApi.getUser(userId),
    staleTime: 5 * 60 * 1000,
  })

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: (updates: Partial<User>) =>
      userApi.updateUser(userId, updates),

    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user', userId] })
      toast.success('Profile updated')
      setIsEditing(false)
      onUpdate?.()
    },

    onError: () => {
      toast.error('Failed to update profile')
    },
  })

  const fullName = `${user.firstName} ${user.lastName}`

  const handleEdit = () => setIsEditing(true)

  const handleSave = () => {
    updateMutation.mutate({
      firstName: user.firstName,
      lastName: user.lastName,
    })
  }

  const handleCancel = () => setIsEditing(false)

  return (
    <Card className="max-w-lg mx-auto">
      <CardHeader>
        <div className="flex items-center gap-4">
          <Avatar className="h-16 w-16">
            <AvatarFallback>
              {user.firstName[0]}{user.lastName[0]}
            </AvatarFallback>
          </Avatar>
          <div>
            <CardTitle>{fullName}</CardTitle>
            <p className="text-sm text-muted-foreground">{user.email}</p>
          </div>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        <div className="space-y-2">
          <p className="text-sm">Username: {user.username}</p>
          <p className="text-sm">Roles: {user.roles.join(', ')}</p>
        </div>

        <div className="flex gap-2">
          {!isEditing ? (
            <Button onClick={handleEdit}>Edit Profile</Button>
          ) : (
            <>
              <Button
                onClick={handleSave}
                disabled={updateMutation.isPending}
              >
                {updateMutation.isPending ? 'Saving...' : 'Save'}
              </Button>
              <Button variant="outline" onClick={handleCancel}>
                Cancel
              </Button>
            </>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
```

**Usage:**
```typescript
import { Suspense } from 'react'
import { LoadingState } from '@/components/common/LoadingState'

<Suspense fallback={<LoadingState />}>
  <UserProfile userId="123" onUpdate={() => console.log('Updated')} />
</Suspense>
```

---

## Example 2: Form with Validation

Using react-hook-form + Zod + shadcn/ui:

```typescript
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

const userSchema = z.object({
  username: z.string().min(3, 'Username must be at least 3 characters'),
  email: z.string().email('Invalid email address'),
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
})

type UserFormData = z.infer<typeof userSchema>

interface CreateUserFormProps {
  onSuccess?: () => void
}

export function CreateUserForm({ onSuccess }: CreateUserFormProps) {
  const queryClient = useQueryClient()

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<UserFormData>({
    resolver: zodResolver(userSchema),
    defaultValues: {
      username: '',
      email: '',
      firstName: '',
      lastName: '',
    },
  })

  const createMutation = useMutation({
    mutationFn: (data: UserFormData) => userApi.createUser(data),

    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      toast.success('User created successfully')
      reset()
      onSuccess?.()
    },

    onError: () => {
      toast.error('Failed to create user')
    },
  })

  const onSubmit = (data: UserFormData) => {
    createMutation.mutate(data)
  }

  return (
    <Card className="max-w-md">
      <CardHeader>
        <CardTitle>Create User</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="username">Username</Label>
            <Input id="username" {...register('username')} />
            {errors.username && (
              <p className="text-xs text-destructive">{errors.username.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input id="email" type="email" {...register('email')} />
            {errors.email && (
              <p className="text-xs text-destructive">{errors.email.message}</p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="firstName">First Name</Label>
              <Input id="firstName" {...register('firstName')} />
              {errors.firstName && (
                <p className="text-xs text-destructive">{errors.firstName.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="lastName">Last Name</Label>
              <Input id="lastName" {...register('lastName')} />
              {errors.lastName && (
                <p className="text-xs text-destructive">{errors.lastName.message}</p>
              )}
            </div>
          </div>

          <Button type="submit" className="w-full" disabled={createMutation.isPending}>
            {createMutation.isPending ? 'Creating...' : 'Create User'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
```

---

## Example 3: Dialog with Form

```typescript
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { UserPlus, X } from 'lucide-react'

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const formSchema = z.object({
  name: z.string().min(1, 'Name is required'),
  email: z.string().email('Invalid email'),
})

type FormData = z.infer<typeof formSchema>

interface AddUserDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (data: FormData) => Promise<void>
}

export function AddUserDialog({ open, onOpenChange, onSubmit }: AddUserDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<FormData>({
    resolver: zodResolver(formSchema),
  })

  const handleClose = () => {
    reset()
    onOpenChange(false)
  }

  const handleFormSubmit = async (data: FormData) => {
    setIsSubmitting(true)
    try {
      await onSubmit(data)
      handleClose()
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <UserPlus className="h-5 w-5" />
            Add User
          </DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit(handleFormSubmit)}>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="name">Name</Label>
              <Input id="name" {...register('name')} autoFocus />
              {errors.name && (
                <p className="text-xs text-destructive">{errors.name.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" {...register('email')} />
              {errors.email && (
                <p className="text-xs text-destructive">{errors.email.message}</p>
              )}
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={handleClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Adding...' : 'Add User'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
```

---

## Example 4: List with Search

```typescript
import { useState, useDeferredValue } from 'react'
import { useSuspenseQuery } from '@tanstack/react-query'

import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

export function UserList() {
  const [searchTerm, setSearchTerm] = useState('')
  const deferredSearch = useDeferredValue(searchTerm)

  const { data: users } = useSuspenseQuery({
    queryKey: ['users'],
    queryFn: () => userApi.getUsers(),
  })

  // Filter using deferred value to prevent UI jank
  const filteredUsers = users.filter((user) => {
    if (!deferredSearch) return true
    const search = deferredSearch.toLowerCase()
    return (
      user.firstName.toLowerCase().includes(search) ||
      user.lastName.toLowerCase().includes(search) ||
      user.email.toLowerCase().includes(search)
    )
  })

  const isStale = searchTerm !== deferredSearch

  return (
    <div className="space-y-4">
      <Input
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        placeholder="Search users..."
        className="max-w-sm"
      />

      <div
        className={cn(
          'grid gap-4 sm:grid-cols-2 lg:grid-cols-3',
          isStale && 'opacity-70'
        )}
      >
        {filteredUsers.map((user) => (
          <Card key={user.id}>
            <CardContent className="pt-4">
              <p className="font-medium">{user.firstName} {user.lastName}</p>
              <p className="text-sm text-muted-foreground">{user.email}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {filteredUsers.length === 0 && (
        <p className="text-center text-muted-foreground py-8">
          No users found
        </p>
      )}
    </div>
  )
}
```

---

## Example 5: Parallel Data Fetching

```typescript
import { Suspense } from 'react'
import { useSuspenseQueries } from '@tanstack/react-query'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { LoadingState } from '@/components/common/LoadingState'

function DashboardContent() {
  const [statsQuery, usersQuery, activityQuery] = useSuspenseQueries({
    queries: [
      {
        queryKey: ['stats'],
        queryFn: () => statsApi.getStats(),
      },
      {
        queryKey: ['users', 'active'],
        queryFn: () => userApi.getActiveUsers(),
      },
      {
        queryKey: ['activity', 'recent'],
        queryFn: () => activityApi.getRecent(),
      },
    ],
  })

  return (
    <div className="grid gap-4 md:grid-cols-3">
      <Card>
        <CardHeader>
          <CardTitle className="text-sm font-medium">Stats</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-2xl font-bold">{statsQuery.data.total}</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-sm font-medium">Active Users</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-2xl font-bold">{usersQuery.data.length}</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-sm font-medium">Recent Activity</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-2xl font-bold">{activityQuery.data.length}</p>
        </CardContent>
      </Card>
    </div>
  )
}

export function Dashboard() {
  return (
    <Suspense fallback={<LoadingState />}>
      <DashboardContent />
    </Suspense>
  )
}
```

---

## Example 6: Optimistic Update

```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import type { User } from '@/types/UserTypes'

export function useToggleUserStatus() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (userId: string) => userApi.toggleStatus(userId),

    onMutate: async (userId) => {
      await queryClient.cancelQueries({ queryKey: ['users'] })

      const previousUsers = queryClient.getQueryData<User[]>(['users'])

      queryClient.setQueryData<User[]>(['users'], (old) => {
        return old?.map((user) =>
          user.id === userId ? { ...user, active: !user.active } : user
        ) ?? []
      })

      return { previousUsers }
    },

    onError: (err, userId, context) => {
      queryClient.setQueryData(['users'], context?.previousUsers)
      toast.error('Failed to update user status')
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },

    onSuccess: () => {
      toast.success('User status updated')
    },
  })
}
```

---

## Example 7: Route with Lazy Loading

```typescript
import { createFileRoute } from '@tanstack/react-router'
import { lazy, Suspense } from 'react'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'

const UserProfile = lazy(() =>
  import('@/features/users/components/UserProfile').then((module) => ({
    default: module.UserProfile,
  }))
)

export const Route = createFileRoute('/users/$userId')({
  component: UserProfilePage,
})

function UserProfilePage() {
  const { userId } = Route.useParams()

  if (!userId) {
    return <ErrorState message="User ID is required" />
  }

  return (
    <Suspense fallback={<LoadingState />}>
      <UserProfile userId={userId} />
    </Suspense>
  )
}
```

---

## Summary

| Pattern | Implementation |
|---------|---------------|
| Component typing | Explicit props interface |
| Data fetching | useSuspenseQuery + Suspense |
| Forms | react-hook-form + Zod |
| Styling | Tailwind + shadcn/ui |
| Toasts | sonner |
| Routing | TanStack Router + lazy |
| Performance | React Compiler (automatic) |

**See Also:**
- [component-patterns.md](component-patterns.md) - Component structure
- [data-fetching.md](data-fetching.md) - Query patterns
- [common-patterns.md](common-patterns.md) - Forms and dialogs
