import { useEffect, useRef } from 'react'

export function useScrollReveal() {
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            (entry.target as HTMLElement).classList.add('scroll-reveal-visible')
            io.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.05 }
    )

    function observeChildren() {
      container!.querySelectorAll('.scroll-reveal:not(.scroll-reveal-visible)').forEach((el) => {
        io.observe(el)
      })
    }

    observeChildren()

    const mo = new MutationObserver(observeChildren)
    mo.observe(container, { childList: true, subtree: true })

    // Fallback: reveal all after 2s in case IntersectionObserver fails (mobile Safari)
    const fallback = setTimeout(() => {
      container!.querySelectorAll('.scroll-reveal:not(.scroll-reveal-visible)').forEach((el) => {
        (el as HTMLElement).classList.add('scroll-reveal-visible')
      })
    }, 2000)

    return () => {
      io.disconnect()
      mo.disconnect()
      clearTimeout(fallback)
    }
  }, [])

  return containerRef
}
