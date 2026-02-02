---
paths:
  - "frontend/src/components/**/*.tsx"
  - "frontend/src/routes/**/*.tsx"
---

# React Hook Form + Zod Patterns

## Form Template

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
  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(formSchema),
    defaultValues: { username: '', email: '' },
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
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
      <div className="space-y-1">
        <Label htmlFor="username">{t('form.username')}</Label>
        <Input id="username" {...register('username')} />
        {errors.username && (
          <p className="text-xs text-destructive">{errors.username.message}</p>
        )}
      </div>

      <Button type="submit">{t('form.submit')}</Button>
    </form>
  )
}
```
