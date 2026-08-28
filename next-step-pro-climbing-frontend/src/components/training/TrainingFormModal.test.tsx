import { describe, it, expect, vi } from 'vitest'
import { useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TrainingFormModal } from './TrainingFormModal'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  // src/i18n.ts is pulled in transitively and calls this at import time
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

vi.mock('../../api/client', () => ({
  adminTrainingCalendarApi: { getTemplates: vi.fn(async () => []) },
}))

/** Mirrors the real caller: the shell stays mounted and is driven by `isOpen`. */
function Harness() {
  const [open, setOpen] = useState(true)
  return (
    <>
      <button onClick={() => setOpen(true)}>reopen</button>
      <TrainingFormModal
        isOpen={open}
        onClose={() => setOpen(false)}
        initialDate="2026-09-01"
        onSubmit={vi.fn()}
        saving={false}
        onUpload={vi.fn()}
      />
    </>
  )
}

function renderForm() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <Harness />
    </QueryClientProvider>,
  )
}

const closeButton = () => screen.getAllByRole('button', { name: /close/i })[0]
const asking = () => screen.queryByText('unsaved.title') !== null

describe('TrainingFormModal — closing does not throw the plan away', () => {
  it('should close a form nobody touched without asking', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.click(closeButton())

    expect(asking()).toBe(false)
    expect(screen.queryByPlaceholderText('form.titlePlaceholder')).toBeNull()
  })

  it('should ask once something is typed', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByPlaceholderText('form.titlePlaceholder'), 'Siła')
    await user.click(closeButton())

    expect(asking()).toBe(true)
  })

  // The reason this form uses a snapshot instead of the bubbling-onChange trick: the kind switch
  // is a <button>, which never fires a change event. If this test goes red, the guard has quietly
  // stopped covering everything that is not a plain input.
  it('should ask after only the Training/Task switch was used', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.click(screen.getByRole('button', { name: /form.kind.TASK/ }))
    await user.click(closeButton())

    expect(asking()).toBe(true)
  })

  it('should keep the text when the writer goes back to editing', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByPlaceholderText('form.titlePlaceholder'), 'Siła')
    await user.click(closeButton())
    await user.click(screen.getByText('unsaved.keep'))

    expect(screen.getByPlaceholderText('form.titlePlaceholder')).toHaveValue('Siła')
  })

  // The shell outlives the form, so a discarded draft could leave the next, empty one asking
  // about changes nobody made — the reason useChildDirty resets on the isOpen flip
  it('should not carry the discarded state into the next open', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByPlaceholderText('form.titlePlaceholder'), 'Siła')
    await user.click(closeButton())
    await user.click(screen.getByText('unsaved.discard'))

    await user.click(screen.getByText('reopen'))
    expect(screen.getByPlaceholderText('form.titlePlaceholder')).toHaveValue('')

    await user.click(closeButton())
    expect(asking()).toBe(false)
  })
})

/**
 * Cancel is six pixels from Save and does the same irreversible thing as the X, so it must ask
 * the same question. It used to call `onClose` straight through, past the guard entirely.
 */
describe('TrainingFormModal — Cancel is guarded too', () => {
  it('should ask before discarding when Cancel is clicked on a dirty form', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByPlaceholderText('form.titlePlaceholder'), 'Siła')
    await user.click(screen.getByText('form.cancel'))

    expect(asking()).toBe(true)
    expect(screen.getByPlaceholderText('form.titlePlaceholder')).toHaveValue('Siła')
  })

  it('should still close straight away when Cancel is clicked on a pristine form', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.click(screen.getByText('form.cancel'))

    expect(asking()).toBe(false)
    expect(screen.queryByPlaceholderText('form.titlePlaceholder')).toBeNull()
  })
})

/**
 * A fresh create with no clicked hour opens in all-day mode, and submit() drops both times there.
 * Applying a TRAINING template therefore has to switch all-day OFF, or the duration — the one
 * thing such a template carries beyond its text — is thrown away without a word.
 */
describe('TrainingFormModal — applying a template that carries a duration', () => {
  const TEMPLATE = {
    id: 'tpl-1',
    kind: 'TRAINING' as const,
    title: 'Siła',
    description: null,
    defaultDurationMinutes: 90,
    targetCalories: null,
    attachments: [],
    updatedAt: '2026-07-01T09:00:00Z',
  }

  function renderWithTemplates() {
    const onSubmit = vi.fn()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <TrainingFormModal
          isOpen
          onClose={vi.fn()}
          initialDate="2026-09-01"
          onSubmit={onSubmit}
          saving={false}
          onUpload={vi.fn()}
          templatesEnabled
        />
      </QueryClientProvider>,
    )
    return { onSubmit }
  }

  it('should turn all-day off and submit the template span', async () => {
    const { adminTrainingCalendarApi } = await import('../../api/client')
    vi.mocked(adminTrainingCalendarApi.getTemplates).mockResolvedValue([TEMPLATE])
    const user = userEvent.setup()
    const { onSubmit } = renderWithTemplates()

    const picker = await screen.findByRole('combobox')
    await user.selectOptions(picker, 'tpl-1')

    // The checkbox is the visible half of the same decision
    expect(screen.getByRole('checkbox', { name: /form.allDay/ })).not.toBeChecked()

    await user.click(screen.getByText('form.save'))

    expect(onSubmit).toHaveBeenCalledTimes(1)
    const payload = onSubmit.mock.calls[0][0]
    expect(payload.startTime).toBe('17:00')
    expect(payload.endTime).toBe('18:30')
  })
})
