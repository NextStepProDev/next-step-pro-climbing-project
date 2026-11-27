import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

/**
 * Asks before unsent writing is left behind, and lets the person stay or leave.
 *
 * Three exits are covered:
 * - a refresh, a closed tab, a typed address (`beforeunload`, the browser's own prompt);
 * - a click on any in-app link, the navbar included — caught on `document` in the CAPTURE phase,
 *   so it runs before React Router's `<Link>` and can stop it. This app mounts a plain
 *   `<BrowserRouter>`, where `useBlocker` throws, so the link itself is the only place to ask;
 * - the host's own buttons (a tab switch, another conversation), routed through `guard(action)`.
 *
 * ⚠️ NOT covered: the browser's back/forward within the app. `popstate` cannot be cancelled, and
 * undoing it means guessing how far the history moved.
 *
 * `isDirty` is a getter read at the moment of the click (see useChildDirty): a flag carried through
 * state would arrive one render late.
 */
export function useLeaveGuard(isDirty: () => boolean) {
  const navigate = useNavigate()
  const [pending, setPending] = useState<(() => void) | null>(null)
  // The getter may be a fresh function each render; the listeners must always call the latest one
  const isDirtyRef = useRef(isDirty)
  useEffect(() => {
    isDirtyRef.current = isDirty
  }, [isDirty])

  useEffect(() => {
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (!isDirtyRef.current()) return
      e.preventDefault()
      e.returnValue = ''
    }

    const onClick = (e: MouseEvent) => {
      // A new tab or window leaves this page as it is — nothing to ask about
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
      const anchor = (e.target as Element | null)?.closest?.('a[href]')
      if (!(anchor instanceof HTMLAnchorElement)) return
      if ((anchor.target && anchor.target !== '_self') || anchor.hasAttribute('download')) return
      const url = new URL(anchor.href, window.location.href)
      // Another site unloads this one, and beforeunload asks; mailto: and tel: leave nothing
      if (url.origin !== window.location.origin) return
      const to = url.pathname + url.search + url.hash
      if (to === window.location.pathname + window.location.search + window.location.hash) return
      if (!isDirtyRef.current()) return
      e.preventDefault()
      e.stopPropagation()
      setPending(() => () => navigate(to))
    }

    window.addEventListener('beforeunload', onBeforeUnload)
    document.addEventListener('click', onClick, true)
    return () => {
      window.removeEventListener('beforeunload', onBeforeUnload)
      document.removeEventListener('click', onClick, true)
    }
  }, [navigate])

  /** Runs `action` at once when nothing is unsent; otherwise asks first. */
  const guard = useCallback((action: () => void) => {
    if (isDirtyRef.current()) setPending(() => action)
    else action()
  }, [])

  const stay = useCallback(() => setPending(null), [])
  const leave = useCallback(() => {
    const action = pending
    setPending(null)
    action?.()
  }, [pending])

  return { guard, asking: pending !== null, stay, leave }
}
