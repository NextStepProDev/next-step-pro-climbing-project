import { useEffect } from 'react'

/**
 * One page-scroll lock shared by everything that covers the page (modals, the confirmation, the
 * lightbox, the mobile menu). Counted, not toggled: each of them used to write `''` back to
 * `body` overflow on closing, so a confirmation stacked over an open modal unlocked the page
 * behind the modal that was still there. The page scrolls again only when the last lock goes.
 */
let locks = 0

export function lockBodyScroll(): () => void {
  locks++
  document.body.style.overflow = 'hidden'
  let released = false
  return () => {
    if (released) return
    released = true
    locks--
    if (locks === 0) document.body.style.overflow = ''
  }
}

/** Holds the lock exactly while `active` is true. */
export function useBodyScrollLock(active: boolean): void {
  useEffect(() => {
    if (!active) return
    return lockBodyScroll()
  }, [active])
}
