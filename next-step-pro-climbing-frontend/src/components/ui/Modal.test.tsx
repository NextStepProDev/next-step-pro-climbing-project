import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Modal } from './Modal'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

function open(confirmClose: boolean, onClose = vi.fn()) {
  render(
    <Modal isOpen onClose={onClose} title="Formularz" confirmClose={confirmClose}>
      <p>treść</p>
    </Modal>,
  )
  return onClose
}

// The dimmed layer that carries the click handler — NOT the `fixed inset-0` root above it,
// which has none and would make these assertions pass for the wrong reason
const backdrop = () => document.querySelector('.absolute.inset-0')!
const closeButton = () => screen.getByRole('button', { name: /close/i })
const confirmShowing = () => screen.queryByText('unsaved.title') !== null

/**
 * `confirmClose` guards three separate exits and had no test at all, while three production
 * modals rely on it. Each exit is asserted on its own — they are wired independently inside
 * Modal, so one of them silently bypassing the guard is exactly the shape of bug that ships.
 */
describe('Modal — guarding unsaved work', () => {
  describe('with nothing to lose', () => {
    it('should close on the X without asking', async () => {
      const onClose = open(false)
      await userEvent.click(closeButton())
      expect(onClose).toHaveBeenCalledOnce()
      expect(confirmShowing()).toBe(false)
    })

    it('should close on a backdrop click without asking', async () => {
      const onClose = open(false)
      await userEvent.click(backdrop())
      expect(onClose).toHaveBeenCalledOnce()
    })

    it('should close on Escape without asking', async () => {
      const onClose = open(false)
      await userEvent.keyboard('{Escape}')
      expect(onClose).toHaveBeenCalledOnce()
    })
  })

  describe('with unsaved work', () => {
    it('should ask instead of closing on the X', async () => {
      const onClose = open(true)
      await userEvent.click(closeButton())
      expect(onClose).not.toHaveBeenCalled()
      expect(confirmShowing()).toBe(true)
    })

    // The accidental one: a stray click beside the modal is the most common way work is lost
    it('should ask instead of closing on a backdrop click', async () => {
      const onClose = open(true)
      await userEvent.click(backdrop())
      expect(onClose).not.toHaveBeenCalled()
      expect(confirmShowing()).toBe(true)
    })

    it('should ask instead of closing on Escape', async () => {
      const onClose = open(true)
      await userEvent.keyboard('{Escape}')
      expect(onClose).not.toHaveBeenCalled()
      expect(confirmShowing()).toBe(true)
    })

    it('should close once discarding is confirmed', async () => {
      const onClose = open(true)
      await userEvent.click(closeButton())
      await userEvent.click(screen.getByText('unsaved.discard'))
      expect(onClose).toHaveBeenCalledOnce()
    })

    it('should keep the content when the writer goes back to editing', async () => {
      const onClose = open(true)
      await userEvent.click(closeButton())
      await userEvent.click(screen.getByText('unsaved.keep'))

      expect(onClose).not.toHaveBeenCalled()
      expect(confirmShowing()).toBe(false)
      expect(screen.getByText('treść')).toBeInTheDocument()
    })

    // Escape is the reflex for "get me out of here", and on the confirmation that must mean
    // backing out of the question — never answering "discard" on the writer's behalf
    it('should treat Escape on the confirmation as going back, not discarding', async () => {
      const onClose = open(true)
      await userEvent.click(closeButton())
      await userEvent.keyboard('{Escape}')

      expect(onClose).not.toHaveBeenCalled()
      expect(confirmShowing()).toBe(false)
      expect(screen.getByText('treść')).toBeInTheDocument()
    })
  })
})

/**
 * The guard is read by a click handler, so a form that reports its dirty state upward must be
 * able to make that report land before the very next event — not one render later. This is the
 * shape that shipped broken: `confirmClose` as a plain boolean lagged a render behind, and
 * typing then hitting Escape in the same tick discarded the work in silence.
 *
 * RTL flushes effects between simulated steps, so it cannot reproduce the original race — what
 * it CAN pin is the contract that makes the race impossible: a function is asked at close time.
 */
describe('Modal — confirmClose as a getter', () => {
  it('should ask the function at the moment of closing, not when it rendered', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    let dirty = false

    render(
      <Modal isOpen onClose={onClose} title="Formularz" confirmClose={() => dirty}>
        <p>treść</p>
      </Modal>,
    )

    // Flipped without any re-render — exactly what a ref written during a child's render does
    dirty = true
    await user.click(closeButton())

    expect(onClose).not.toHaveBeenCalled()
    expect(confirmShowing()).toBe(true)
  })

  it('should still close when the function says there is nothing to lose', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()

    render(
      <Modal isOpen onClose={onClose} title="Formularz" confirmClose={() => false}>
        <p>treść</p>
      </Modal>,
    )

    await user.click(closeButton())
    expect(onClose).toHaveBeenCalledOnce()
  })
})
