import { useEffect, useState } from 'react'
import { Check } from 'lucide-react'

export function SuccessCheckmark({ onDone }: { onDone?: () => void }) {
  const [visible, setVisible] = useState(true)

  useEffect(() => {
    const timer = setTimeout(() => {
      setVisible(false)
      onDone?.()
    }, 1500)
    return () => clearTimeout(timer)
  }, [onDone])

  if (!visible) return null

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center pointer-events-none">
      <div className="absolute inset-0 bg-black/40" />
      <div className="relative w-20 h-20 rounded-full bg-green-500 flex items-center justify-center animate-[success-pulse_0.6s_ease-out]">
        <Check className="w-10 h-10 text-white stroke-[3]" />
      </div>
    </div>
  )
}
