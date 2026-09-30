import { useEffect, useLayoutEffect, useRef } from 'react'
import { Check } from 'lucide-react'

/**
 * A 1.5 s "done" checkmark, then `onDone`. The caller unmounts it there — it does not hide
 * itself, because a checkmark that turned itself into nothing while the caller still rendered
 * it left the registration page blank. Hence `onDone` is required: without it the dimmed
 * overlay would stay up for good.
 *
 * The timer starts once, on mount. Every caller passes an inline arrow, and with `onDone` as an
 * effect dependency any re-render of the parent restarted the 1.5 s.
 */
export function SuccessCheckmark({ onDone }: { onDone: () => void }) {
  const done = useRef(onDone)
  useLayoutEffect(() => {
    done.current = onDone
  })

  useEffect(() => {
    const timer = setTimeout(() => done.current(), 1500)
    return () => clearTimeout(timer)
  }, [])

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center pointer-events-none">
      <div className="absolute inset-0 bg-black/40" />
      <div className="relative w-20 h-20 rounded-full bg-green-500 flex items-center justify-center animate-[success-pulse_0.6s_ease-out]">
        <Check className="w-10 h-10 text-white stroke-[3]" />
      </div>
    </div>
  )
}
