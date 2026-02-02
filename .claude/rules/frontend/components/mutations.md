---
paths:
  - "frontend/src/hooks/**/*.ts"
  - "frontend/src/components/**/*.tsx"
---

# Mutation + Toast Patterns

## Mutation Hook Template

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

## Usage in Component

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

## Toast Methods

```typescript
import { toast } from 'sonner'

// Success/Error/Info
toast.success('Operation completed')
toast.error('Something went wrong')
toast.info('Processing...', { description: 'Please wait' })

// Promise-based
toast.promise(saveData(), {
  loading: 'Saving...',
  success: 'Saved!',
  error: 'Failed to save',
})
```
