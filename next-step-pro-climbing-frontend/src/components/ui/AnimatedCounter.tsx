import { useEffect, useRef, useState } from 'react'

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * A number that counts up from zero once it scrolls into view (the home page's stats).
 *
 * Two audiences the animation must not reach:
 * - a screen reader reads whatever the digits say at that moment — "0+" before the count, "57+"
 *   halfway — so the digits are hidden from it and it gets the final number as text instead;
 * - someone who asked the system for less motion (vestibular disorders) gets the final number
 *   straight away, the same courtesy the home-page hero already pays them.
 */
export function AnimatedCounter({ target, suffix = '', duration = 2000 }: { target: number; suffix?: string; duration?: number }) {
  const [reduced] = useState(prefersReducedMotion)
  const [count, setCount] = useState(() => (reduced ? target : 0))
  const [started, setStarted] = useState(false)
  const ref = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    const el = ref.current
    if (!el || reduced) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setStarted(true)
          observer.disconnect()
        }
      },
      { threshold: 0.3 }
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [reduced])

  useEffect(() => {
    if (!started) return
    const start = performance.now()
    let frame = 0
    function tick(now: number) {
      const elapsed = now - start
      const progress = Math.min(elapsed / duration, 1)
      const eased = 1 - Math.pow(1 - progress, 3)
      setCount(Math.round(eased * target))
      if (progress < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [started, target, duration])

  return (
    <span ref={ref}>
      <span aria-hidden="true">{count}{suffix}</span>
      <span className="sr-only">{target}{suffix}</span>
    </span>
  )
}
