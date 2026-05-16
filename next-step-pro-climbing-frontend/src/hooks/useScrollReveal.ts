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
      { threshold: 0.1, rootMargin: '0px 0px -40px 0px' }
    )

    function observeChildren() {
      container!.querySelectorAll('.scroll-reveal:not(.scroll-reveal-visible)').forEach((el) => {
        io.observe(el)
      })
    }

    observeChildren()

    const mo = new MutationObserver(observeChildren)
    mo.observe(container, { childList: true, subtree: true })

    return () => {
      io.disconnect()
      mo.disconnect()
    }
  }, [])

  return containerRef
}
