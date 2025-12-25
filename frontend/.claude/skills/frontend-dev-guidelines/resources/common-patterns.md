# Common Patterns

Frequently used patterns for forms, dialogs, mutations, and state management using shadcn/ui, TanStack Query, and sonner.

---

## Forms with React Hook Form + Zod

### Basic Form Pattern

```typescript
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'

const formSchema = z.object({
  username: z.string().min(3, 'Username must be at least 3 characters'),
  email: z.string().email('Invalid email address'),
})

type FormData = z.infer<typeof formSchema>

export function MyForm() {
  const { t } = useTranslation()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      username: '',
      email: '',
    },
  })

  const onSubmit = async (data: FormData) => {
    try {
      await submitForm(data)
      toast.success(t('form.submitSuccess'))
    } catch {
      toast.error(t('form.submitError'))
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 max-w-sm">
      <div className="space-y-1">
        <Label htmlFor="username">{t('form.username')}</Label>
        <Input id="username" {...register('username')} />
        {errors.username && (
          <p className="text-xs text-destructive">
            {errors.username.message}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <Label htmlFor="email">{t('form.email')}</Label>
        <Input id="email" type="email" {...register('email')} />
        {errors.email && (
          <p className="text-xs text-destructive">
            {errors.email.message}
          </p>
        )}
      </div>

      <Button type="submit">{t('form.submit')}</Button>
    </form>
  )
}
```

---

## Dialog Pattern (shadcn)

```typescript
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useTranslation } from 'react-i18next'

interface MyDialogProps {
  open: boolean
  onClose: () => void
  onConfirm: () => void
}

export function MyDialog({ open, onClose, onConfirm }: MyDialogProps) {
  const { t } = useTranslation()

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('dialog.title')}</DialogTitle>
        </DialogHeader>

        <div className="py-4">
          {/* Dialog content */}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('dialog.cancel')}
          </Button>
          <Button onClick={onConfirm}>
            {t('dialog.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

---

## Mutation Patterns (TanStack Query + sonner)

```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

export function useUpdateEntity() {
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: EntityData }) =>
      updateEntity(id, data),

    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['entity', variables.id] })
      queryClient.invalidateQueries({ queryKey: ['entities'] })
      toast.success(t('entity.updateSuccess'))
    },

    onError: () => {
      toast.error(t('entity.updateError'))
    },
  })
}
```

### Usage

```typescript
function EntityEditor({ id }: { id: string }) {
  const updateEntity = useUpdateEntity()

  const handleSave = (data: EntityData) => {
    updateEntity.mutate({ id, data })
  }

  return (
    <Button
      onClick={() => handleSave(formData)}
      disabled={updateEntity.isPending}
    >
      {updateEntity.isPending ? 'Saving...' : 'Save'}
    </Button>
  )
}
```

---

## Toast Notifications (sonner)

```typescript
import { toast } from 'sonner'

// Success
toast.success('Operation completed')

// Error
toast.error('Something went wrong')

// With description
toast.info('Processing...', {
  description: 'Please wait while we process your request',
})

// Promise-based
toast.promise(saveData(), {
  loading: 'Saving...',
  success: 'Saved successfully',
  error: 'Failed to save',
})
```

---

## Confirmation Dialog

```typescript
import { useState } from 'react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { useTranslation } from 'react-i18next'

interface ConfirmDialogProps {
  open: boolean
  onClose: () => void
  onConfirm: () => void
  title: string
  description: string
}

export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  description,
}: ConfirmDialogProps) {
  const { t } = useTranslation()

  return (
    <AlertDialog open={open} onOpenChange={(v) => !v && onClose()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel onClick={onClose}>
            {t('dialog.cancel')}
          </AlertDialogCancel>
          <AlertDialogAction onClick={onConfirm}>
            {t('dialog.confirm')}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
```

---

## State Management Patterns

### Local UI State

```typescript
import { useState } from 'react'

function MyComponent() {
  const [isOpen, setIsOpen] = useState(false)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  return (
    // Use local state for component-specific UI
  )
}
```

### Server State (TanStack Query)

```typescript
// Server state lives in TanStack Query
const { data, isPending } = useQuery({
  queryKey: ['entities'],
  queryFn: fetchEntities,
})
```

### Global State (Zustand - if needed)

```typescript
import { create } from 'zustand'

interface AppState {
  theme: 'light' | 'dark'
  setTheme: (theme: 'light' | 'dark') => void
}

export const useAppStore = create<AppState>((set) => ({
  theme: 'dark',
  setTheme: (theme) => set({ theme }),
}))
```

---

## Search/Filter Pattern

```typescript
import { useState, useMemo } from 'react'
import { useDebounce } from 'use-debounce'
import { Input } from '@/components/ui/input'
import { useTranslation } from 'react-i18next'

interface FilterableListProps<T> {
  items: T[]
  filterFn: (item: T, search: string) => boolean
  renderItem: (item: T) => React.ReactNode
}

export function FilterableList<T extends { id: string }>({
  items,
  filterFn,
  renderItem,
}: FilterableListProps<T>) {
  const { t } = useTranslation()
  const [search, setSearch] = useState('')
  const [debouncedSearch] = useDebounce(search, 300)

  const filteredItems = useMemo(() => {
    if (!debouncedSearch) return items
    return items.filter(item => filterFn(item, debouncedSearch))
  }, [items, debouncedSearch, filterFn])

  return (
    <div className="space-y-4">
      <Input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder={t('common.search')}
      />

      <div className="grid grid-cols-4 gap-4">
        {filteredItems.map(item => (
          <div key={item.id}>{renderItem(item)}</div>
        ))}
      </div>
    </div>
  )
}
```

---

## Summary

| Pattern | Tool |
|---------|------|
| Forms | React Hook Form + Zod |
| Dialogs | shadcn Dialog/AlertDialog |
| Toasts | sonner |
| Server state | TanStack Query |
| Local UI state | useState |
| Global state | Zustand (minimal) |
| Search/filter | useDebounce + useMemo |

**See Also:**
- [schemas-and-validation.md](schemas-and-validation.md) - Zod patterns
- [data-fetching.md](data-fetching.md) - TanStack Query patterns
- [styling-guide.md](styling-guide.md) - shadcn/ui usage
