import { useSyncExternalStore } from 'react'

// Mirrors Tailwind's `sm`. Below it a day cell is about 45px wide: enough to say that
// something is planned, never enough to say what.
const COMPACT_QUERY = '(max-width: 639px)'

function subscribe(onChange: () => void): () => void {
  const mq = window.matchMedia(COMPACT_QUERY)
  mq.addEventListener('change', onChange)
  return () => mq.removeEventListener('change', onChange)
}

function isCompact(): boolean {
  return window.matchMedia(COMPACT_QUERY).matches
}

/**
 * True below Tailwind's `sm` breakpoint.
 *
 * A media query in JS rather than CSS because the month view does not merely restyle
 * here — it swaps to a different component with different interactions. Rendering both
 * trees and hiding one with CSS would double the work and leave the hidden half reachable
 * by keyboard and screen readers.
 *
 * useSyncExternalStore rather than useEffect + useState so the subscription and the read
 * cannot disagree: a change landing between render and effect is not missed.
 *
 * ⚠️ Reactive by design — use it to choose which component renders. Do NOT feed it
 * straight into the week/month choice: that would flip the view someone picked when they
 * rotate their phone. See resolveInitialView in components/training/monthGrid.ts.
 */
export function useCompactViewport(): boolean {
  return useSyncExternalStore(subscribe, isCompact, () => false)
}
