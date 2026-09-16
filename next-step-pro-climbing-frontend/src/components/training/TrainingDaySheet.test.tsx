import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TrainingDaySheet } from './TrainingDaySheet'
import { makeTraining } from '../../test/factories'

// getErrorMessage pulls in src/i18n, which needs the real initReactI18next — only the hook is faked.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const DATE = '2026-07-20'

function renderSheet(props: Partial<React.ComponentProps<typeof TrainingDaySheet>> = {}) {
  const onClose = vi.fn()
  const onTrainingDelete = vi.fn().mockResolvedValue(undefined)
  const onTrainingClick = vi.fn()
  const view = render(
    <TrainingDaySheet
      date={DATE}
      trainings={[makeTraining({ date: DATE, title: 'Endurance circuits' })]}
      reservations={[]}
      invitations={[]}
      invitationLabel="overlay.invitation"
      onClose={onClose}
      onTrainingClick={onTrainingClick}
      onReservationClick={vi.fn()}
      onInvitationClick={vi.fn()}
      onAdd={vi.fn()}
      onTrainingDelete={onTrainingDelete}
      {...props}
    />,
  )
  return { ...view, onClose, onTrainingDelete, onTrainingClick }
}

/**
 * Clearing a run of entries that should not be there is why this exists: an armed clipboard
 * turns every tap into another pasted copy, and undoing that one card at a time is brutal.
 */
describe('TrainingDaySheet — deleting an entry in place', () => {
  it('should delete only after the confirmation is accepted', async () => {
    const { onTrainingDelete } = renderSheet()

    await userEvent.click(screen.getByLabelText('detail.delete'))
    expect(onTrainingDelete).not.toHaveBeenCalled()

    await userEvent.click(screen.getByText('confirm'))

    expect(onTrainingDelete).toHaveBeenCalledTimes(1)
    expect(onTrainingDelete.mock.calls[0][0].title).toBe('Endurance circuits')
  })

  it('should leave the sheet open so the next mistake can go too', async () => {
    // Deliberately NOT routed through closeThen like every other action here: cleaning up five
    // entries must not cost five trips back into the day.
    const { onClose } = renderSheet()

    await userEvent.click(screen.getByLabelText('detail.delete'))
    await userEvent.click(screen.getByText('confirm'))

    expect(onClose).not.toHaveBeenCalled()
  })

  it('should say so when the delete is rejected', async () => {
    // The detail modal's error line sits on the page BEHIND this sheet, so without its own
    // the failure would look exactly like a delete that worked.
    renderSheet({ onTrainingDelete: vi.fn().mockRejectedValue(new Error('Could not delete the training')) })

    await userEvent.click(screen.getByLabelText('detail.delete'))
    await userEvent.click(screen.getByText('confirm'))

    expect(await screen.findByText('Could not delete the training')).toBeInTheDocument()
  })

  it('should offer no delete button when the host passes no handler', () => {
    renderSheet({ onTrainingDelete: undefined })

    expect(screen.queryByLabelText('detail.delete')).not.toBeInTheDocument()
  })
})

/**
 * On a phone the month is a dot grid: six pixels per entry, nowhere to name a paste target.
 * So the sheet carries it — the add button becomes "paste here", the same swap the month cell
 * and the week's all-day lane make. Without this an armed clipboard had no target at all on a
 * phone, which is how "paste anywhere" got invented in the first place.
 */
describe('TrainingDaySheet — the phone\'s paste target', () => {
  it('should offer to paste instead of to add while the clipboard is armed', async () => {
    const onPaste = vi.fn()
    const { onClose } = renderSheet({ pasteActive: true, onPaste })

    expect(screen.queryByText('month.addOnDay')).not.toBeInTheDocument()
    await userEvent.click(screen.getByText('clipboard.pasteHere'))

    expect(onPaste).toHaveBeenCalledWith(DATE)
    // Like every other action here: the sheet gets out of the way before it acts
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('should offer to add when the clipboard is empty', () => {
    renderSheet({ pasteActive: false })

    expect(screen.getByText('month.addOnDay')).toBeInTheDocument()
    expect(screen.queryByText('clipboard.pasteHere')).not.toBeInTheDocument()
  })
})
