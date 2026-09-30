import { useEffect, useLayoutEffect, useRef, type RefObject } from 'react'

/**
 * Calls `onOutside` on a mousedown anywhere outside `ref` — how every dropdown in the app closes.
 *
 * `mousedown`, not `click`: the menu closes before the press lands elsewhere, so opening a second
 * menu does not briefly show two. `enabled` keeps the document listener off while the menu is
 * closed. The callback is read through a ref, so an inline arrow does not re-subscribe on
 * every render.
 */
export function useClickOutside(
  ref: RefObject<HTMLElement | null>,
  onOutside: () => void,
  enabled = true,
): void {
  const callback = useRef(onOutside)
  useLayoutEffect(() => {
    callback.current = onOutside
  })

  useEffect(() => {
    if (!enabled) return
    function handleMouseDown(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) callback.current()
    }
    document.addEventListener('mousedown', handleMouseDown)
    return () => document.removeEventListener('mousedown', handleMouseDown)
  }, [ref, enabled])
}
