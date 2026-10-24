import { useCallback, useEffect, useRef, useState } from 'react'
import { useCurrentUserId } from '../context/AuthContext'
import { readDraft, removeDraft, writeDraft, type StoredDraft } from '../utils/draftStorage'

interface DraftOptions<T> {
  /**
   * What is being written, unique per entity: `news:<id>`, `training:new:<athleteId>`. Null turns
   * the draft off (nothing to identify yet). Each scope holds one draft per account.
   */
  scope: string | null
  /** The editor's writing, JSON-serialisable — File objects do not survive and must be left out. */
  value: T
  /** Kept only while the editor differs from what is saved; a clean editor removes the draft. */
  dirty: boolean
  /** The saved entity's version (`updatedAt`) right now; null for something not saved yet. */
  baseline?: string | null
  /**
   * false: the host seeds its state from the draft itself ({@link useInitialDraft}) — a message
   * composer, where restoring the text you were typing needs no question.
   */
  offer?: boolean
}

export interface DraftOffer<T = unknown> {
  savedAt: number
  /** The entity was saved after the draft began: restoring would overwrite that change. */
  stale: boolean
  /** What restoring would bring back — for the banner to describe it, not to apply it. */
  data: T
}

interface PendingState<T> {
  identity: string | null
  pending: StoredDraft<T> | null
}

/**
 * Keeps an editor's unsaved writing on this device and offers it back the next time the same
 * thing is opened. See draftStorage for who may read it and how long it lives.
 *
 * Written on every change, not on a timer: the moments that lose text (a navbar click unmounting
 * the editor, iOS discarding the tab) give no warning, so a pending debounce would be lost with it.
 *
 * While an offered draft waits for an answer, nothing is written — typing first would otherwise
 * overwrite the very text the banner is offering.
 */
export function useDraft<T>({ scope, value, dirty, baseline = null, offer = true }: DraftOptions<T>) {
  const userId = useCurrentUserId()
  const identity = userId && scope ? `${userId}\u0000${scope}` : null
  const load = (): StoredDraft<T> | null =>
    offer && userId && scope ? readDraft<T>(userId, scope) : null

  const [state, setState] = useState<PendingState<T>>(() => ({ identity, pending: load() }))
  let current = state
  if (state.identity !== identity) {
    // A different entity in the same mounted editor: its own draft, never the previous one's.
    current = { identity, pending: load() }
    setState(current)
  }
  const pending = current.pending

  const serialized = JSON.stringify(value)
  // After a save, the editor can stay "dirty" for a render or two until the refetch lands; the
  // value at the moment of saving must not be written straight back as a draft.
  const [savedValue, setSavedValue] = useState<string | null>(null)
  const latestRef = useRef(serialized)

  useEffect(() => {
    latestRef.current = serialized
    if (!userId || !scope || pending) return
    if (!dirty) {
      removeDraft(userId, scope)
      return
    }
    if (savedValue === serialized) return
    writeDraft<T>(userId, scope, { savedAt: Date.now(), baseline, data: JSON.parse(serialized) as T })
  }, [userId, scope, serialized, dirty, baseline, pending, savedValue])

  const restore = useCallback((): T | null => {
    const data = pending?.data ?? null
    setState({ identity, pending: null })
    return data
  }, [identity, pending])

  const discard = useCallback(() => {
    if (userId && scope) removeDraft(userId, scope)
    setState({ identity, pending: null })
  }, [identity, userId, scope])

  /** Call once the writing is safely saved (or sent). */
  const clear = useCallback(() => {
    if (userId && scope) removeDraft(userId, scope)
    setSavedValue(latestRef.current)
    setState({ identity, pending: null })
  }, [identity, userId, scope])

  /**
   * Undo a {@link clear} whose save turned out to fail — for forms that hand the save to a parent
   * and learn the outcome only from an error prop. Without it the text still on screen would sit
   * unprotected until the next keystroke. Safe to call during render (it only sets state).
   */
  const resume = useCallback(() => setSavedValue(null), [])

  const draftOffer: DraftOffer<T> | null = pending
    ? { savedAt: pending.savedAt, stale: pending.baseline !== baseline, data: pending.data }
    : null

  return { offer: draftOffer, restore, discard, clear, resume }
}

/** The stored draft's data, read once on mount — for hosts using `offer: false`. */
export function useInitialDraft<T>(scope: string | null): T | null {
  const userId = useCurrentUserId()
  const [initial] = useState(() => (userId && scope ? readDraft<T>(userId, scope)?.data ?? null : null))
  return initial
}
