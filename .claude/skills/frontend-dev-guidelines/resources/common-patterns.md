# Common Patterns

Frequently used patterns for authentication, forms, dialogs, data tables, mutations, and state management using **shadcn/ui**, **TanStack Query**, **Sonner**, and **modern React**.

---

## Authentication with `useAuth`

### Getting Current User

```ts
import { useAuth } from '@/hooks/useAuth';

export const MyComponent: React.FC = () => {
  const { user } = useAuth();

  // Available properties:
  // - user.id: string
  // - user.email: string
  // - user.username: string
  // - user.roles: string[]

  return (
    <div className="space-y-1 text-sm">
      <p>Logged in as: {user.email}</p>
      <p>Username: {user.username}</p>
      <p>Roles: {user.roles.join(', ')}</p>
    </div>
  );
};
```

**RULE:**  
❌ NEVER make direct API calls for authentication  
✅ ALWAYS use the `useAuth` hook

---

## Forms with React Hook Form + Zod

### Basic Form Pattern (shadcn + Sonner)

```ts
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { toast } from 'sonner';

const formSchema = z.object({
  username: z.string().min(3, 'Username must be at least 3 characters'),
  email: z.string().email('Invalid email address'),
  age: z.number().min(18, 'Must be 18 or older'),
});

type FormData = z.infer<typeof formSchema>;

export const MyForm: React.FC = () => {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      username: '',
      email: '',
      age: 18,
    },
  });

  const onSubmit = async (data: FormData) => {
    try {
      await api.submitForm(data);
      toast.success('Form submitted successfully');
    } catch {
      toast.error('Failed to submit form');
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 max-w-sm">
      <div className="space-y-1">
        <Label htmlFor="username">Username</Label>
        <Input id="username" {...register('username')} />
        {errors.username && (
          <p className="text-xs text-destructive">
            {errors.username.message}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <Label htmlFor="email">Email</Label>
        <Input id="email" type="email" {...register('email')} />
        {errors.email && (
          <p className="text-xs text-destructive">
            {errors.email.message}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <Label htmlFor="age">Age</Label>
        <Input
          id="age"
          type="number"
          {...register('age', { valueAsNumber: true })}
        />
        {errors.age && (
          <p className="text-xs text-destructive">
            {errors.age.message}
          </p>
        )}
      </div>

      <Button type="submit">Submit</Button>
    </form>
  );
};
```

---

## Dialog Component Pattern (shadcn)

```ts
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { X, Info } from 'lucide-react';

interface MyDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

export const MyDialog: React.FC<MyDialogProps> = ({
  open,
  onClose,
  onConfirm,
}) => {
  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent>
        <DialogHeader className="flex flex-row items-center justify-between">
          <DialogTitle className="flex items-center gap-2">
            <Info className="h-4 w-4 text-primary" />
            Dialog Title
          </DialogTitle>
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            className="h-8 w-8"
          >
            <X className="h-4 w-4" />
          </Button>
        </DialogHeader>

        <div className="py-4">{/* Content here */}</div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={onConfirm}>Confirm</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
```

---

## Mutation Patterns (TanStack Query + Sonner)

```ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';

export const useUpdateEntity = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: any }) =>
      api.updateEntity(id, data),

    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['entity', variables.id] });
      queryClient.invalidateQueries({ queryKey: ['entities'] });

      toast.success('Entity updated');
    },

    onError: () => {
      toast.error('Failed to update entity');
    },
  });
};
```

---

## Summary

- ✅ `useAuth` for authentication
- ✅ React Hook Form + Zod
- ✅ shadcn Dialog standard
- ✅ TanStack Query for server state
- ✅ **Sonner for toast notifications**
- ✅ `useState` for local UI state
- ✅ Zustand for minimal global state

**See Also:**
- [data-fetching.md](data-fetching.md) - TanStack Query patterns
- [component-patterns.md](component-patterns.md) - Component structure
- [loading-and-error-states.md](loading-and-error-states.md) - Error handling
