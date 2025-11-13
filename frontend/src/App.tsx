import { Button } from '@/components/ui/button'

function App() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100">
      <div className="text-center">
        <h1 className="text-4xl font-bold text-gray-900 mb-4">
          Limbus Planner
        </h1>
        <p className="text-gray-600 mb-6">
          Tailwind CSS and shadcn/ui configured successfully!
        </p>
        <Button onClick={() => alert('Button clicked!')}>
          Click Me
        </Button>
      </div>
    </div>
  )
}

export default App
