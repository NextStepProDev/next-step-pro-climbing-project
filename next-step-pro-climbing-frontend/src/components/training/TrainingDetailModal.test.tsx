import { useState } from 'react'
import { describe, it, expect, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { format, subDays } from 'date-fns'
import { TrainingDetailModal } from './TrainingDetailModal'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import { makeTraining } from '../../test/factories'
import type { PersonalTraining } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  // src/i18n.ts is pulled in transitively (utils/errors) and initialises on import
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const api = {
  getComments: vi.fn().mockResolvedValue([]),
  addComment: vi.fn(),
} as unknown as TrainingCalendarAdapter

/**
 * Mirrors how TrainingCalendarSection drives the modal: onComplete is a mutateAsync,
 * so a rejected save both throws into the modal and lands in the section's error state.
 */
function Harness({ training, onComplete, isCoachView }: {
  training: PersonalTraining
  onComplete: (training: PersonalTraining, data: { feedback?: string; rpe?: number }) => Promise<unknown>
  isCoachView?: boolean
}) {
  const [error, setError] = useState<string | null>(null)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <QueryClientProvider client={client}>
      <TrainingDetailModal
        training={training}
        onClose={vi.fn()}
        api={api}
        isCoachView={isCoachView}
        onEdit={vi.fn()}
        onDuplicate={vi.fn()}
        onDelete={vi.fn()}
        errorMessage={error}
        onComplete={async (tr, data) => {
          setError(null)
          try {
            return await onComplete(tr, data)
          } catch (e) {
            setError('save failed')
            throw e
          }
        }}
      />
    </QueryClientProvider>
  )
}

const yesterday = () => format(subDays(new Date(), 1), 'yyyy-MM-dd')

/** Open the completion form and pick an RPE (Save stays disabled without one). */
async function openFormAndRate(rpe = 7) {
  const user = userEvent.setup()
  await user.click(screen.getByText('completion.markDone'))
  await user.click(screen.getByRole('button', { name: String(rpe) }))
  return user
}

describe('TrainingDetailModal — failed completion keeps the form open (#98)', () => {
  const training = makeTraining({ date: yesterday(), startTime: '10:00', endTime: '11:00' })

  it('should keep the completion form open when the save fails', async () => {
    const onComplete = vi.fn().mockRejectedValue(new Error('conflict'))
    render(<Harness training={training} onComplete={onComplete} />)

    const user = await openFormAndRate()
    await user.click(screen.getByText('completion.save'))

    await waitFor(() => expect(onComplete).toHaveBeenCalled())
    // The form collapsing here is what made an athlete believe the training was ticked off
    expect(screen.getByText('completion.save')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('completion.feedbackPlaceholder')).toBeInTheDocument()
  })

  it('should show the error inside the form, above Save', async () => {
    const onComplete = vi.fn().mockRejectedValue(new Error('conflict'))
    render(<Harness training={training} onComplete={onComplete} />)

    const user = await openFormAndRate()
    await user.click(screen.getByText('completion.save'))

    // getByText also asserts it is rendered exactly once: the quiet line below the comment
    // thread must not double up while the form is open
    const error = await screen.findByText('save failed')
    const save = screen.getByText('completion.save')
    expect(error.compareDocumentPosition(save) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    // Walk up from the feedback field to the first block that also holds Save: that block IS the
    // form, whatever the field happens to be wrapped in. Counting parentElement hops instead ties
    // this assertion to the field's markup — it broke the day the field grew a toolbar.
    let form = screen.getByPlaceholderText('completion.feedbackPlaceholder').parentElement!
    while (!form.contains(save)) form = form.parentElement!
    expect(form).toContainElement(error)
  })

  it('should leave the training visibly not completed after a failed save', async () => {
    const onComplete = vi.fn().mockRejectedValue(new Error('conflict'))
    render(<Harness training={training} onComplete={onComplete} />)

    const user = await openFormAndRate()
    await user.click(screen.getByText('completion.save'))

    await waitFor(() => expect(screen.getByText('save failed')).toBeInTheDocument())
    // The "done" summary (completedAt + undo) belongs to a COMPLETED training only
    expect(screen.queryByText('completion.completedAt')).not.toBeInTheDocument()
    expect(screen.queryByText('completion.undo')).not.toBeInTheDocument()
  })

  it('should allow a retry that succeeds and then close the form', async () => {
    const onComplete = vi.fn()
      .mockRejectedValueOnce(new Error('conflict'))
      .mockResolvedValueOnce(undefined)
    render(<Harness training={training} onComplete={onComplete} />)

    const user = await openFormAndRate()
    await user.click(screen.getByText('completion.save'))
    await screen.findByText('save failed')

    await user.click(screen.getByText('completion.save'))

    await waitFor(() => expect(screen.queryByText('completion.save')).not.toBeInTheDocument())
    expect(onComplete).toHaveBeenCalledTimes(2)
    expect(screen.queryByText('save failed')).not.toBeInTheDocument()
  })

  it('should close the form on a successful save', async () => {
    const onComplete = vi.fn().mockResolvedValue(undefined)
    render(<Harness training={training} onComplete={onComplete} />)

    const user = await openFormAndRate()
    await user.click(screen.getByText('completion.save'))

    await waitFor(() => expect(screen.queryByText('completion.save')).not.toBeInTheDocument())
    expect(screen.queryByPlaceholderText('completion.feedbackPlaceholder')).not.toBeInTheDocument()
  })

  it('should send the picked RPE and typed feedback', async () => {
    const onComplete = vi.fn().mockResolvedValue(undefined)
    render(<Harness training={training} onComplete={onComplete} />)

    const user = await openFormAndRate(9)
    await user.type(screen.getByPlaceholderText('completion.feedbackPlaceholder'), 'legs were dead')
    await user.click(screen.getByText('completion.save'))

    await waitFor(() => expect(onComplete).toHaveBeenCalledWith(
      training, { feedback: 'legs were dead', rpe: 9 },
    ))
  })
})

describe('TrainingDetailModal — completion gating', () => {
  it('should require an RPE before the save button is usable (V71)', async () => {
    render(<Harness training={makeTraining({ date: yesterday() })} onComplete={vi.fn()} />)

    await userEvent.click(screen.getByText('completion.markDone'))

    expect(screen.getByText('completion.save').closest('button')).toBeDisabled()
    expect(screen.getByText('completion.rpeRequired')).toBeInTheDocument()
  })

  it('should not offer completion before the training has started', () => {
    const tomorrow = format(subDays(new Date(), -1), 'yyyy-MM-dd')
    render(<Harness training={makeTraining({ date: tomorrow, startTime: '10:00', endTime: '11:00' })} onComplete={vi.fn()} />)

    expect(screen.queryByText('completion.markDone')).not.toBeInTheDocument()
    expect(screen.getByText('completion.availableAfterStart')).toBeInTheDocument()
  })

  it('should treat an untimed training as started from the beginning of its day (V72)', () => {
    render(<Harness training={makeTraining({ date: yesterday(), startTime: null, endTime: null })} onComplete={vi.fn()} />)

    expect(screen.getByText('completion.markDone')).toBeInTheDocument()
    expect(screen.getByText('detail.allDay')).toBeInTheDocument()
  })

  it('should never let the coach tick off a training', () => {
    // Completion is the athlete's word on what actually happened
    render(<Harness training={makeTraining({ date: yesterday() })} onComplete={vi.fn()} isCoachView />)

    expect(screen.queryByText('completion.markDone')).not.toBeInTheDocument()
    expect(screen.queryByText('completion.availableAfterStart')).not.toBeInTheDocument()
  })

  it('should show the coach the athlete\'s feedback but no undo button', () => {
    render(
      <Harness
        training={makeTraining({
          date: yesterday(), status: 'COMPLETED', completedAt: '2026-07-20T13:00:00Z',
          rpe: 8, feedback: 'tough but good',
        })}
        onComplete={vi.fn()}
        isCoachView
      />,
    )

    expect(screen.getByText('completion.athleteFeedback')).toBeInTheDocument()
    expect(screen.getByText('tough but good')).toBeInTheDocument()
    expect(screen.queryByText('completion.undo')).not.toBeInTheDocument()
    expect(screen.queryByText('completion.edit')).not.toBeInTheDocument()
  })

  it('should let the athlete edit or undo a completed training', () => {
    render(
      <Harness
        training={makeTraining({ date: yesterday(), status: 'COMPLETED', completedAt: '2026-07-20T13:00:00Z', rpe: 8 })}
        onComplete={vi.fn()}
      />,
    )

    expect(screen.getByText('completion.edit')).toBeInTheDocument()
    expect(screen.getByText('completion.undo')).toBeInTheDocument()
  })

  it('should abandon the form without saving when cancel is pressed', async () => {
    const onComplete = vi.fn()
    render(<Harness training={makeTraining({ date: yesterday() })} onComplete={onComplete} />)

    const user = await openFormAndRate()
    await user.click(screen.getByText('form.cancel'))

    expect(onComplete).not.toHaveBeenCalled()
    expect(screen.getByText('completion.markDone')).toBeInTheDocument()
  })
})

/**
 * This modal is the only one in the feature that had no guard at all, and it holds three separate
 * pieces of unsaved work: a completion being filled in, a message being typed, and a correction to
 * a message already sent. Escape, the backdrop and the X threw away all three without asking.
 */
describe('TrainingDetailModal — unsaved work is not thrown away silently', () => {
  const asking = () => screen.queryByText('unsaved.title') !== null
  const closeButton = () => screen.getAllByRole('button', { name: /close/i })[0]

  it('should close without asking when nothing has been touched', async () => {
    const user = userEvent.setup()
    render(<Harness training={makeTraining({ date: yesterday() })} onComplete={vi.fn()} />)

    await user.click(closeButton())

    expect(asking()).toBe(false)
  })

  it('should ask when a completion is half filled in', async () => {
    const user = await openFormAndRateOn(makeTraining({ date: yesterday() }))

    await user.click(closeButton())

    expect(asking()).toBe(true)
  })

  it('should ask when a message has been typed but not sent', async () => {
    const user = userEvent.setup()
    render(<Harness training={makeTraining({ date: yesterday() })} onComplete={vi.fn()} />)

    await user.type(await screen.findByPlaceholderText('comments.placeholder'), 'jeszcze jedno')
    await user.click(closeButton())

    expect(asking()).toBe(true)
  })

  // Opening the form and changing nothing is not unsaved work — an athlete reading their own
  // feedback back must still be able to shut the card in one click.
  it('should not ask when the completion form was opened but left untouched', async () => {
    const user = userEvent.setup()
    render(
      <Harness
        training={makeTraining({
          date: yesterday(), status: 'COMPLETED', completedAt: '2026-07-20T13:00:00Z', rpe: 8,
        })}
        onComplete={vi.fn()}
      />,
    )

    await user.click(screen.getByText('completion.edit'))
    await user.click(closeButton())

    expect(asking()).toBe(false)
  })

  async function openFormAndRateOn(training: PersonalTraining) {
    render(<Harness training={training} onComplete={vi.fn()} />)
    return openFormAndRate()
  }
})
