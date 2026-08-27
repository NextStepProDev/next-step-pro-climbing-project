import { createContext, useContext } from 'react'

/**
 * The guarded close of the surrounding `Modal`, for controls the modal does not draw itself.
 *
 * A form's own "Cancel" button calls the `onClose` its parent handed down, which goes straight
 * past `confirmClose` — so the X asked before discarding and Cancel, six pixels from Save, did
 * not. That is the mis-click that costs the most: the two buttons sit side by side, and one of
 * them was silently destructive.
 *
 * Lives beside Modal rather than inside it because exporting a hook from a file that exports a
 * component trips `react-refresh/only-export-components` — the same reason `ACTION_CONFIG` was
 * moved out of `AdminActivityPanel`.
 */
export const ModalCloseContext = createContext<(() => void) | null>(null)

/**
 * Returns the surrounding modal's guarded close, or `null` outside a `Modal`.
 *
 * Callers fall back to their own `onClose` so a form still works when rendered without one.
 */
export function useModalClose(): (() => void) | null {
  return useContext(ModalCloseContext)
}
