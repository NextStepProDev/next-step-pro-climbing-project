import { useEffect } from 'react'

/**
 * Asks the browser to confirm before the page is abandoned while work is unsaved —
 * a refresh, a closed tab, a typed address, a back that leaves the site.
 *
 * Covers exactly those exits and no others. Clicking a link in the navbar, or going back
 * WITHIN the app, never reaches the browser: those are in-app route changes, and intercepting
 * them needs a data router (`useBlocker` throws outside one, and this app mounts a plain
 * `<BrowserRouter>`). Editors therefore still guard their own "back to list" button with a
 * confirmation of their own — this hook is the second half, not the whole story.
 *
 * The wording is the browser's own generic one and cannot be customised; passing a string
 * has been ignored by every major browser for years.
 */
export function useUnsavedChangesWarning(enabled: boolean) {
  useEffect(() => {
    if (!enabled) return
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      // preventDefault is the current spec; returnValue is the legacy trigger some
      // browsers still key off. Setting both is what actually gets the prompt shown.
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [enabled])
}
