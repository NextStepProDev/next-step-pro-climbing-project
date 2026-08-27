import { useCallback, useEffect, useRef } from 'react'

/**
 * Carries a form's "dirty" flag up to the modal shell that renders `<Modal>`.
 *
 * The training-side modals all have the same shape: the shell owns no form state, and the form
 * itself is mounted with `{isOpen && <Form/>}` so its state resets on every open. That is what
 * makes {@link useDirty} usable inside it — but it also means the flag has to travel up, because
 * `confirmClose` belongs to the shell.
 *
 * ⚠️ **A ref read at close time, not state read at render time.** Routing this through
 * `useEffect` + `setState` costs a render to propagate, and the guard is consulted by an event
 * handler — so typing a title and hitting Escape in the same tick asked nothing and threw the
 * work away. Verified against the running app; React Testing Library cannot reproduce it,
 * because it flushes effects between every simulated step. The child therefore reports during
 * its own render and `Modal` takes a getter it calls on the click.
 *
 * The reset covers the one case the child cannot: it unmounts on close without ever reporting
 * `false`, so a form abandoned as dirty would otherwise leave the next visitor to this shell
 * asking about changes nobody made — in `TrainingTemplatesModal` that is the template LIST,
 * which must stay closable without a prompt. Re-opening is already handled by the child, whose
 * first render reports a clean snapshot before any event can reach the guard.
 */
export function useChildDirty(isOpen: boolean): [() => boolean, (dirty: boolean) => void] {
  const dirty = useRef(false)

  useEffect(() => {
    if (!isOpen) dirty.current = false
  }, [isOpen])

  // Both stable: reporting must never cause a render, and the getter must not close over a stale value
  const isDirty = useCallback(() => dirty.current, [])
  const report = useCallback((value: boolean) => { dirty.current = value }, [])

  return [isDirty, report]
}
