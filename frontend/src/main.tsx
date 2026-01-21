import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { RouterProvider } from '@tanstack/react-router'
import { Toaster } from 'sonner'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'
import { queryClient } from '@/lib/queryClient'
import { router } from '@/lib/router'
import './lib/i18n'
import './styles/globals.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ErrorBoundary>
        <RouterProvider router={router} />
      </ErrorBoundary>
      <Toaster
        position="top-right"
        toastOptions={{
          unstyled: true,
          classNames: {
            toast: 'flex items-center gap-3 w-[356px] p-4 bg-card text-card-foreground border border-border rounded-lg shadow-lg',
            title: 'text-sm font-medium',
            description: 'text-sm text-muted-foreground',
            actionButton: 'bg-primary text-primary-foreground px-3 py-1.5 rounded-md text-sm',
            cancelButton: 'bg-muted text-muted-foreground px-3 py-1.5 rounded-md text-sm',
            success: 'border-primary/60 [&_[data-icon]]:text-primary',
            error: 'border-destructive/60 [&_[data-icon]]:text-destructive',
            warning: 'border-yellow-500/60 [&_[data-icon]]:text-yellow-500',
            info: 'border-primary/60 [&_[data-icon]]:text-primary',
          },
        }}
      />
      {/* Dev tools - only in development */}
      {import.meta.env.DEV && (
        <ReactQueryDevtools initialIsOpen={false} />
      )}
    </QueryClientProvider>
  </StrictMode>,
)
