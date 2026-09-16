import { useSyncExternalStore } from 'react'

// A mouse or a trackpad: something that can hover, and can hit a small target on purpose.
const POINTER_FINE_QUERY = '(hover: hover) and (pointer: fine)'

function subscribe(onChange: () => void): () => void {
  const mq = window.matchMedia(POINTER_FINE_QUERY)
  mq.addEventListener('change', onChange)
  return () => mq.removeEventListener('change', onChange)
}

function isPointerFine(): boolean {
  return window.matchMedia(POINTER_FINE_QUERY).matches
}

/**
 * True where a precise, hovering pointer is driving the page.
 *
 * A media query in JS rather than CSS because the clipboard controls on a calendar entry are
 * not a styling difference: on a mouse they are hover-revealed chips that cannot be hit by
 * accident, and on a touch screen they must not EXIST — an athlete armed the clipboard by
 * tapping one while aiming at the entry, and from then on the calendar answered every tap with
 * another pasted copy. Touch arms the clipboard from inside the open card instead.
 *
 * ⚠️ Hiding them with `pointer-fine:` CSS would not do: the buttons would still be in the DOM,
 * still tappable, still on the keyboard path — the same trap with the paint scraped off.
 *
 * Falls back to `false` on the server and in any environment without matchMedia, which is the
 * safe side: no micro-buttons, and the card still carries copy and cut.
 */
export function usePointerFine(): boolean {
  return useSyncExternalStore(subscribe, isPointerFine, () => false)
}
