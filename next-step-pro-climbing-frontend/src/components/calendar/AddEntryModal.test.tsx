import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AddEntryModal } from './AddEntryModal'

// The t mock echoes the key, so every label below is queried by its key.
vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
}))

function renderChooser() {
  const onPickSlot = vi.fn()
  const onPickEvent = vi.fn()
  const onClose = vi.fn()
  render(
    <AddEntryModal
      isOpen
      onClose={onClose}
      date="2030-06-10"
      onPickSlot={onPickSlot}
      onPickEvent={onPickEvent}
    />,
  )
  return { onPickSlot, onPickEvent, onClose }
}

describe('AddEntryModal — the "+" asks before it opens a form', () => {
  it('should offer both a slot and an event', () => {
    renderChooser()

    expect(screen.getByRole('button', { name: /addEntry\.slot/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /addEntry\.event/ })).toBeInTheDocument()
  })

  it('should route each tile to its own form and to no other', async () => {
    const user = userEvent.setup()
    const { onPickSlot, onPickEvent } = renderChooser()

    await user.click(screen.getByRole('button', { name: /addEntry\.slotHint/ }))
    expect(onPickSlot).toHaveBeenCalledTimes(1)
    expect(onPickEvent).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: /addEntry\.eventHint/ }))
    expect(onPickEvent).toHaveBeenCalledTimes(1)
    expect(onPickSlot).toHaveBeenCalledTimes(1)
  })

  // A 'yyyy-MM-dd' from the API is a label, not a moment: `new Date(s)` would read it as midnight
  // UTC and name the day before anywhere west of Greenwich.
  it('should name the day the admin opened, not the one before it', () => {
    renderChooser()

    expect(screen.getByText(/^addEntry\.title:10/)).toBeInTheDocument()
  })

  // `?date=` is hand-editable and reaches this component raw. `format` on an Invalid Date throws,
  // and a throw in render takes the whole calendar page down — not just this modal.
  it('should fall back to the raw label rather than throw on an unparseable date', () => {
    expect(() =>
      render(
        <AddEntryModal
          isOpen
          onClose={vi.fn()}
          date="2030-06-31"
          onPickSlot={vi.fn()}
          onPickEvent={vi.fn()}
        />,
      ),
    ).not.toThrow()

    expect(screen.getByText('addEntry.title:2030-06-31')).toBeInTheDocument()
  })
})
