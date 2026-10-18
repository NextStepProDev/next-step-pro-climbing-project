import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Modal } from './Modal'
import { ModalCancelButton } from './ModalCancelButton'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

function renderIn(dirty: boolean, onClose = vi.fn()) {
  render(
    <Modal isOpen onClose={onClose} title="Edycja" confirmClose={() => dirty}>
      <ModalCancelButton onClose={onClose}>Anuluj</ModalCancelButton>
    </Modal>,
  )
  return onClose
}

describe('ModalCancelButton', () => {
  it('asks before throwing away unsaved edits, like the cross does', async () => {
    // A plain onClick={onClose} beside "Save" skipped the question — the likeliest click was the
    // one that lost the work.
    const onClose = renderIn(true)
    await userEvent.click(screen.getByRole('button', { name: 'Anuluj' }))

    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByText('unsaved.title')).toBeInTheDocument()
  })

  it('closes straight away when nothing changed', async () => {
    const onClose = renderIn(false)
    await userEvent.click(screen.getByRole('button', { name: 'Anuluj' }))

    expect(onClose).toHaveBeenCalledOnce()
  })
})
