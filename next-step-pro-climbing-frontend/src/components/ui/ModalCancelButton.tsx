import type { ReactNode } from 'react'
import { Button } from './Button'
import { useModalClose } from './modalClose'

/**
 * "Cancel" that goes through the modal's guarded close, so it asks about unsaved changes exactly
 * like the cross, Escape and the backdrop do.
 *
 * A component of its own because the hook reads the modal's context: a panel that renders `<Modal>`
 * itself cannot call it at its own level (it would get null and fall back to the bare close). Placed
 * inside the modal, this does.
 */
export function ModalCancelButton({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  const guardedClose = useModalClose()
  return (
    <Button type="button" variant="ghost" onClick={() => (guardedClose ?? onClose)()}>
      {children}
    </Button>
  )
}
