---
paths:
  - "frontend/src/components/**/*.tsx"
---

# shadcn/ui Component Patterns

## Installation

```bash
# Add individual components
yarn run shadcn add button
yarn run shadcn add card
yarn run shadcn add dialog

# Add multiple at once
yarn run shadcn add button card dialog alert-dialog input label
```

## Common Components

```typescript
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent } from '@/components/ui/alert-dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
```

## Usage Examples

### Card
```typescript
<Card>
  <CardHeader>
    <CardTitle>Title</CardTitle>
  </CardHeader>
  <CardContent>
    <p>Content</p>
  </CardContent>
</Card>
```

### Dialog
```typescript
<Dialog open={open} onOpenChange={setOpen}>
  <DialogContent>
    <DialogHeader>
      <DialogTitle>{t('dialog.title')}</DialogTitle>
    </DialogHeader>
    <div className="py-4">{/* content */}</div>
    <DialogFooter>
      <Button variant="outline" onClick={onClose}>{t('cancel')}</Button>
      <Button onClick={onConfirm}>{t('confirm')}</Button>
    </DialogFooter>
  </DialogContent>
</Dialog>
```

### AlertDialog (Confirmation)
```typescript
<AlertDialog open={open} onOpenChange={setOpen}>
  <AlertDialogContent>
    <AlertDialogHeader>
      <AlertDialogTitle>{title}</AlertDialogTitle>
      <AlertDialogDescription>{description}</AlertDialogDescription>
    </AlertDialogHeader>
    <AlertDialogFooter>
      <AlertDialogCancel onClick={onCancel}>{t('cancel')}</AlertDialogCancel>
      <AlertDialogAction onClick={onConfirm}>{t('confirm')}</AlertDialogAction>
    </AlertDialogFooter>
  </AlertDialogContent>
</AlertDialog>
```

### Button Variants
```typescript
<Button variant="default">Primary</Button>
<Button variant="outline">Secondary</Button>
<Button variant="ghost">Tertiary</Button>
<Button variant="destructive">Delete</Button>
<Button size="sm">Small</Button>
<Button size="lg">Large</Button>
```

### Form Input
```typescript
<div className="space-y-1">
  <Label htmlFor="email">{t('form.email')}</Label>
  <Input id="email" type="email" {...register('email')} />
  {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
</div>
```
