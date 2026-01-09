import { Header } from './Header'
import { Footer } from './Footer'
import { LanguageSync } from './LanguageSync'

interface GlobalLayoutProps {
  children: React.ReactNode
}

export function GlobalLayout({ children }: GlobalLayoutProps) {
  return (
    <div className="min-h-screen flex flex-col">
      <LanguageSync />
      <div className="bg-card border-b border-border">
        <Header />
      </div>
      <main className="flex-1 bg-background">{children}</main>
      <div className="bg-card border-t border-border">
        <Footer />
      </div>
    </div>
  )
}
